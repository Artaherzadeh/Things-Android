package com.things.app.data.db

import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class RepeatRuleDecoderTest {
    private val inspector = RepeatPlistInspector()
    private val decoder = RepeatRuleDecoder()

    private fun inspection(body: String): RepeatPayloadInspection {
        val bytes = ("<plist version=\"1.0\">" + body + "</plist>")
            .toByteArray(Charsets.UTF_8)
        return inspector.inspect(bytes)
    }

    @Test fun decodesKnownScalarTypesWithoutInterpretingThem() {
        val result = decoder.decode(
            inspection(
                "<dict>" +
                    "<key>text</key><string>weekly</string>" +
                    "<key>count</key><integer>3</integer>" +
                    "<key>ratio</key><real>1.50</real>" +
                    "<key>enabled</key><true/>" +
                    "<key>day</key><date>2026-01-02T03:04:05Z</date>" +
                    "<key>blob</key><data>AQID</data>" +
                    "</dict>",
            ),
        )!!

        assertEquals("weekly", result.text("text"))
        assertEquals(BigInteger.valueOf(3), result.integer("count"))
        assertEquals("1.50", result.real("ratio"))
        assertEquals(true, result.boolean("enabled"))
        assertEquals("2026-01-02T03:04:05Z", result.date("day"))
        assertArrayEquals(byteArrayOf(1, 2, 3), result.data("blob"))
    }

    @Test fun preservesUnknownKeysAndNestedStructures() {
        val result = decoder.decode(
            inspection(
                "<dict>" +
                    "<key>of</key><array>" +
                    "<dict><key>wd</key><integer>5</integer></dict>" +
                    "</array>" +
                    "<key>future-key</key><string>keep me</string>" +
                    "</dict>",
            ),
        )!!

        val nested = result.dictionary("of")!!.single() as RepeatRuleValue.Dictionary
        assertEquals(BigInteger.valueOf(5), (nested.values["wd"] as RepeatRuleValue.Integer).value)
        assertEquals("keep me", result.text("future-key"))
    }

    @Test fun retainsLargeIntegerPrecision() {
        val text = "999999999999999999999999999999"
        val result = decoder.decode(
            inspection("<dict><key>x</key><integer>$text</integer></dict>"),
        )!!

        assertEquals(BigInteger(text), result.integer("x"))
    }

    @Test fun retainsRealLexicalRepresentation() {
        val result = decoder.decode(
            inspection("<dict><key>x</key><real>0.100</real></dict>"),
        )!!

        assertEquals("0.100", result.real("x"))
    }

    @Test fun reportsUnknownKeysWithoutGuessingTheirMeaning() {
        val result = decoder.decode(
            inspection(
                "<dict>" +
                    "<key>known</key><integer>1</integer>" +
                    "<key>future-field</key><string>value</string>" +
                    "</dict>",
            ),
        )!!

        assertEquals(setOf("future-field"), result.unknownKeys(setOf("known")))
    }

    @Test fun returnsNullForNonXmlPlistPayloads() {
        assertNull(decoder.decode(inspector.inspect(byteArrayOf())))
        assertNull(decoder.decode(inspector.inspect("bplist00opaque".toByteArray())))
        assertNull(decoder.decode(inspector.inspect("<plist".toByteArray())))
    }

    @Test fun returnsNullWhenRootIsNotADictionary() {
        val bytes = "<plist version=\"1.0\"><string>not a dictionary</string></plist>"
            .toByteArray(Charsets.UTF_8)
        assertNull(decoder.decode(inspector.inspect(bytes)))
    }

    @Test fun doesNotMutateOriginalInspectionPayload() {
        val source = "<plist version=\"1.0\"><dict><key>x</key><string>value</string></dict></plist>"
            .toByteArray(Charsets.UTF_8)
        val inspection = inspector.inspect(source)
        val expected = inspection.originalBytes().copyOf()

        decoder.decode(inspection)

        assertArrayEquals(expected, inspection.originalBytes())
    }
}
