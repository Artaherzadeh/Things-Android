package com.things.app.data.db

import java.math.BigInteger

data class DecodedRepeatRule(
    val fields: Map<String, RepeatRuleValue>,
) {
    fun text(key: String): String? = (fields[key] as? RepeatRuleValue.Text)?.value
    fun integer(key: String): BigInteger? = (fields[key] as? RepeatRuleValue.Integer)?.value
    fun real(key: String): String? = (fields[key] as? RepeatRuleValue.Real)?.lexical
    fun boolean(key: String): Boolean? = (fields[key] as? RepeatRuleValue.Boolean)?.value
    fun date(key: String): String? = (fields[key] as? RepeatRuleValue.Date)?.lexical
    fun data(key: String): ByteArray? = (fields[key] as? RepeatRuleValue.Data)?.bytes
    fun array(key: String): List<RepeatRuleValue>? = (fields[key] as? RepeatRuleValue.Array)?.values
    fun dictionary(key: String): Map<String, RepeatRuleValue>? =
        (fields[key] as? RepeatRuleValue.Dictionary)?.values

    fun unknownKeys(knownKeys: Set<String>): Set<String> =
        fields.keys.filterNot { it in knownKeys }.toSet()
}

sealed interface RepeatRuleValue {
    data class Text(val value: String) : RepeatRuleValue
    data class Integer(val value: BigInteger) : RepeatRuleValue
    /** Lexical text is retained; no floating-point rounding is performed. */
    data class Real(val lexical: String) : RepeatRuleValue
    data class Boolean(val value: kotlin.Boolean) : RepeatRuleValue
    data class Date(val lexical: String) : RepeatRuleValue
    data class Data(val bytes: ByteArray) : RepeatRuleValue
    data class Array(val values: List<RepeatRuleValue>) : RepeatRuleValue
    data class Dictionary(val values: Map<String, RepeatRuleValue>) : RepeatRuleValue
}

class RepeatRuleDecoder {
    fun decode(inspection: RepeatPayloadInspection): DecodedRepeatRule? {
        if (inspection.format != RepeatPayloadFormat.XmlPlist) return null
        val dictionary = inspection.dictionary ?: return null
        return DecodedRepeatRule(
            fields = dictionary.values.mapValues { (_, value) -> decodeValue(value) },
        )
    }

    private fun decodeValue(value: PlistNode): RepeatRuleValue = when (value) {
        is PlistNode.TextValue -> RepeatRuleValue.Text(value.value)
        is PlistNode.IntegerValue -> RepeatRuleValue.Integer(value.value)
        is PlistNode.RealValue -> RepeatRuleValue.Real(value.lexical)
        is PlistNode.BooleanValue -> RepeatRuleValue.Boolean(value.value)
        is PlistNode.DateValue -> RepeatRuleValue.Date(value.lexical)
        is PlistNode.DataValue -> RepeatRuleValue.Data(value.value.bytes())
        is PlistNode.ArrayValue -> RepeatRuleValue.Array(value.values.map(::decodeValue))
        is PlistNode.Dictionary -> RepeatRuleValue.Dictionary(
            value.values.mapValues { (_, child) -> decodeValue(child) },
        )
    }
}
