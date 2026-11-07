package com.things.app.data.db

import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class RepeatPlistInspectorTest {
    private val inspector = RepeatPlistInspector()
    private fun xml(body: String): ByteArray =
        ("<plist version=\"1.0\">" + body + "</plist>").toByteArray(Charsets.UTF_8)
    private fun inspect(body: String) = inspector.inspect(xml(body))

    @Test fun unknownKeysAndNestedValuesAreRetained() {
        val result = inspect("<dict><key>of</key><array><dict><key>wd</key><integer>5</integer></dict></array><key>future-key</key><string>keep me</string></dict>")
        assertEquals(RepeatPayloadFormat.XmlPlist, result.format)
        val root = requireNotNull(result.dictionary).values
        assertEquals(PlistNode.TextValue("keep me"), root["future-key"])
        val list = root.getValue("of") as PlistNode.ArrayValue
        val child = list.values.single() as PlistNode.Dictionary
        assertEquals(PlistNode.IntegerValue(BigInteger.valueOf(5)), child.values["wd"])
    }
    @Test fun standardApplePrologDoesNotRequireFetchingDtd() {
        val source = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
            "<plist version=\"1.0\"><dict/></plist>"
        assertEquals(RepeatPayloadFormat.XmlPlist, inspector.inspect(source.toByteArray()).format)
    }
    @Test fun originalBytesRemainIdenticalAfterSuccessfulInspection() {
        val source = xml("<dict>\n  <key>x</key><string>value  </string>\n</dict>")
        assertArrayEquals(source, inspector.inspect(source).originalBytes())
    }
    @Test fun originalBytesAreCopiedOnInputAndOutput() {
        val source = xml("<dict/>")
        val expected = source.copyOf()
        val result = inspector.inspect(source)
        source[0] = 0
        result.originalBytes()[0] = 0
        assertArrayEquals(expected, result.originalBytes())
    }
    @Test fun binaryPayloadIsRecognizedButNotInterpreted() {
        val source = "bplist00opaque".toByteArray()
        val result = inspector.inspect(source)
        assertEquals(RepeatPayloadFormat.BinaryPlist, result.format)
        assertNull(result.dictionary)
        assertArrayEquals(source, result.originalBytes())
    }
    @Test fun externalEntityDeclarationIsNotAccepted() {
        val source = "<!DOCTYPE plist [<!ENTITY x SYSTEM \"file:///not-a-real-file\">]>" +
            "<plist version=\"1.0\"><dict><key>x</key><string>&x;</string></dict></plist>"
        val result = inspector.inspect(source.toByteArray())
        assertEquals(RepeatPayloadFormat.Unreadable, result.format)
        assertNull(result.dictionary)
    }
    @Test fun duplicateKeysAreNotSilentlyOverwritten() {
        assertEquals(RepeatPayloadFormat.Unreadable,
            inspect("<dict><key>x</key><integer>1</integer><key>x</key><integer>2</integer></dict>").format)
    }
    @Test fun largeIntegerIsNotTruncatedToLong() {
        val text = "999999999999999999999999999999"
        val result = inspect("<dict><key>x</key><integer>$text</integer></dict>")
        assertEquals(PlistNode.IntegerValue(BigInteger(text)), requireNotNull(result.dictionary).values["x"])
    }
    @Test fun base64WhitespaceIsHandledWithoutIgnoringInvalidCharacters() {
        val good = inspect("<dict><key>x</key><data>AQID\n BA==</data></dict>")
        val data = requireNotNull(good.dictionary).values.getValue("x") as PlistNode.DataValue
        assertArrayEquals(byteArrayOf(1,2,3,4), data.value.bytes())
        assertEquals(RepeatPayloadFormat.Unreadable, inspect("<dict><key>x</key><data>AQ!D</data></dict>").format)
    }
    @Test fun depthAndNodeLimitsAreEnforced() {
        val deep = xml("<dict><key>x</key><array><array><string>x</string></array></array></dict>")
        assertEquals(RepeatPayloadFormat.Unreadable, RepeatPlistInspector(maxDepth=1).inspect(deep).format)
        assertEquals(RepeatPayloadFormat.Unreadable, RepeatPlistInspector(maxNodes=1).inspect(xml("<dict><key>x</key><true/></dict>")).format)
    }
    @Test fun oversizedPayloadIsKeptOpaque() {
        val source = xml("<dict/>")
        val result = RepeatPlistInspector(maxBytes=8).inspect(source)
        assertEquals(RepeatPayloadFormat.Unreadable, result.format)
        assertArrayEquals(source, result.originalBytes())
    }
    @Test fun unsupportedValueAndMalformedXmlArePreserved() {
        val source = xml("<dict><key>x</key><uid>12</uid></dict>")
        val result = inspector.inspect(source)
        assertEquals(RepeatPayloadFormat.Unreadable, result.format)
        assertArrayEquals(source, result.originalBytes())
        assertEquals(RepeatPayloadFormat.Unreadable, inspector.inspect("<plist".toByteArray()).format)
    }
    @Test fun emptyPayloadIsDistinctFromMalformedPayload() {
        assertEquals(RepeatPayloadFormat.Empty, inspector.inspect(byteArrayOf()).format)
    }
}
