package com.pasich.encly.core.serialization

import com.google.gson.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.reflect.Type

class MutableStateFlowAdapter : JsonSerializer<MutableStateFlow<String>>,
    JsonDeserializer<MutableStateFlow<String>> {

    override fun serialize(
        src: MutableStateFlow<String>?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return JsonPrimitive(src?.value ?: "")
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): MutableStateFlow<String> {
        return MutableStateFlow(json?.asString ?: "")
    }
}

