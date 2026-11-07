package com.things.app.data.db

import java.io.StringReader
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

sealed interface PlistNode {
    data class Dictionary(val values: Map<String, PlistNode>) : PlistNode
    data class ArrayValue(val values: List<PlistNode>) : PlistNode
    data class TextValue(val value: String) : PlistNode
    data class IntegerValue(val value: BigInteger) : PlistNode
    /** Lexical representation retained, including the source precision. */
    data class RealValue(val lexical: String) : PlistNode
    data class BooleanValue(val value: Boolean) : PlistNode
    data class DateValue(val lexical: String) : PlistNode
    data class DataValue(val value: DbValue.Blob) : PlistNode
}

enum class RepeatPayloadFormat { Empty, XmlPlist, BinaryPlist, Unreadable }

/** Parsed structure is for inspection, not a decoded recurrence schedule. */
data class RepeatPayloadInspection(
    val original: DbValue.Blob,
    val format: RepeatPayloadFormat,
    val dictionary: PlistNode.Dictionary?,
    val issue: String?,
) {
    fun originalBytes(): ByteArray = original.bytes()
}

/**
 * Read-only XML plist inspection. Never serializes the parsed tree back over the original.
 * Unknown recurrence keys are preserved; their meanings are not inferred.
 * Only UTF-8 XML and the standard Apple plist prolog are supported here.
 */
class RepeatPlistInspector(
    private val maxBytes: Int = 1_048_576,
    private val maxDepth: Int = 32,
    private val maxNodes: Int = 4096,
) {
    init { require(maxBytes > 0 && maxDepth > 0 && maxNodes > 0) }

    fun inspect(bytes: ByteArray): RepeatPayloadInspection {
        val original = DbValue.Blob(bytes)
        if (bytes.isEmpty()) return RepeatPayloadInspection(original, RepeatPayloadFormat.Empty, null, "Empty payload.")
        if (bytes.size > maxBytes) return RepeatPayloadInspection(original, RepeatPayloadFormat.Unreadable, null, "Payload exceeds inspection limit.")
        if (bytes.size >= 8 && String(bytes, 0, 8, StandardCharsets.US_ASCII) == "bplist00") {
            return RepeatPayloadInspection(original, RepeatPayloadFormat.BinaryPlist, null, "Binary plist decoding is not implemented.")
        }
        return try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            val text = decoder.decode(ByteBuffer.wrap(bytes)).toString()
            val xml = prepareXml(text)
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            factory.isValidating = false
            factory.isExpandEntityReferences = false
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver { _, _ -> throw SAXException("External entities are not allowed") }
            builder.setErrorHandler(object : DefaultHandler() {
                override fun error(e: org.xml.sax.SAXParseException) { throw e }
                override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }
            })
            val document = builder.parse(InputSource(StringReader(xml)))
            require(document.doctype == null) { "DOCTYPE was not removed by the accepted prolog rule" }
            val root = document.documentElement
            require(root.tagName == "plist" && root.getAttribute("version") == "1.0") { "Unsupported plist root or version" }
            val children = elements(root)
            require(children.size == 1) { "Plist must contain exactly one root value" }
            val parser = ValueParser()
            val value = parser.parse(children.single(), 0)
            require(value is PlistNode.Dictionary) { "Recurrence payload root is not a dictionary" }
            RepeatPayloadInspection(original, RepeatPayloadFormat.XmlPlist, value, null)
        } catch (_: Exception) {
            RepeatPayloadInspection(original, RepeatPayloadFormat.Unreadable, null,
                "XML plist could not be inspected with the supported format and limits; original bytes retained.")
        }
    }

    private fun prepareXml(input: String): String {
        val encoding = Regex("""\A\uFEFF?\s*<\?xml\s+[^?]*encoding\s*=\s*["']([^"']+)["'][^?]*\?>""").find(input)
        require(encoding == null || encoding.groupValues[1].equals("UTF-8", ignoreCase = true)) { "Only UTF-8 XML is supported" }
        // Remove only a known DOCTYPE at the start of the prolog, never content inside a value.
        val appleProlog = Regex("""\A(\uFEFF?\s*(?:<\?xml\s+[^?]*\?>\s*)?)<!DOCTYPE\s+plist\s+PUBLIC\s+"-//Apple//DTD PLIST 1\.0//EN"\s+"http://www\.apple\.com/DTDs/PropertyList-1\.0\.dtd"\s*>""")
        val match = appleProlog.find(input)
        val xml = if (match == null) input else match.groupValues[1] + input.substring(match.range.last + 1)
        require(!xml.contains("<!DOCTYPE") && !xml.contains("<!ENTITY")) { "Unsupported declarations" }
        return xml.removePrefix(0xFEFF.toChar().toString())
    }

    private fun elements(parent: Element): List<Element> = buildList {
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            when (node.nodeType) {
                Node.ELEMENT_NODE -> add(node as Element)
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> require(node.nodeValue.isNullOrBlank()) { "Unexpected text in container" }
                Node.COMMENT_NODE, Node.PROCESSING_INSTRUCTION_NODE -> Unit
                else -> error("Unsupported XML node")
            }
        }
    }
    private fun scalar(element: Element): String {
        require((0 until element.childNodes.length).none { element.childNodes.item(it).nodeType == Node.ELEMENT_NODE }) { "Nested scalar value" }
        return element.textContent
    }

    private inner class ValueParser {
        private var count = 0
        fun parse(element: Element, depth: Int): PlistNode {
            require(depth <= maxDepth && ++count <= maxNodes) { "Plist structure exceeds limits" }
            return when (element.tagName) {
                "dict" -> {
                    val children = elements(element)
                    require(children.size % 2 == 0)
                    val result = linkedMapOf<String, PlistNode>()
                    for (i in children.indices step 2) {
                        val key = children[i]
                        require(key.tagName == "key") { "Dictionary key expected" }
                        require(++count <= maxNodes)
                        val name = scalar(key)
                        require(name !in result) { "Duplicate dictionary key" }
                        result[name] = parse(children[i + 1], depth + 1)
                    }
                    PlistNode.Dictionary(result.toMap())
                }
                "array" -> PlistNode.ArrayValue(elements(element).map { parse(it, depth + 1) })
                "string" -> PlistNode.TextValue(scalar(element))
                "integer" -> {
                    val text = scalar(element).trim()
                    require(text.matches(Regex("[+-]?[0-9]+")))
                    PlistNode.IntegerValue(BigInteger(text))
                }
                "real" -> {
                    val text = scalar(element).trim()
                    require(text.toDoubleOrNull() != null)
                    PlistNode.RealValue(text)
                }
                "true", "false" -> {
                    require(scalar(element).isBlank())
                    PlistNode.BooleanValue(element.tagName == "true")
                }
                "date" -> {
                    val text = scalar(element).trim()
                    Instant.parse(text)
                    PlistNode.DateValue(text)
                }
                "data" -> {
                    val text = scalar(element).replace(Regex("[ \\t\\r\\n]"), "")
                    PlistNode.DataValue(DbValue.Blob(Base64.getDecoder().decode(text)))
                }
                else -> error("Unsupported plist value type")
            }
        }
    }
}

data class InspectedRepeatTemplate(
    val template: StoredRepeatTemplate,
    val legacy: RepeatPayloadInspection?,
    val modern: RepeatPayloadInspection?,
)

/** Adapter over the existing storage reader. Does not change SQLite or infer repeat modes. */
fun RepeatStorageState.inspectPayloads(
    inspector: RepeatPlistInspector = RepeatPlistInspector(),
): Map<String, InspectedRepeatTemplate> = templates.mapValues { (_, template) ->
    InspectedRepeatTemplate(template,
        template.payload.legacyRule?.let { inspector.inspect(it.bytes()) },
        template.payload.repeater?.let { inspector.inspect(it.bytes()) })
}
