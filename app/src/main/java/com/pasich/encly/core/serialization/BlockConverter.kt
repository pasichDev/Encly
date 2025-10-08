package com.pasich.encly.core.serialization

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.pasich.encly.dynamicBlocks.Block
import kotlinx.coroutines.flow.MutableStateFlow


/**
 * Конвертер для преобразования блоков в JSON и обратно
 * Использует кастомные сериализаторы и десериализаторы
 */
object BlockConverter {
    // Создаем Gson-инстанс с кастомными адаптерами для сериализации
    private val gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(Block::class.java, BlockSerializer())
            .registerTypeAdapter(Block::class.java, BlockDeserializer())
            .registerTypeAdapter(MutableStateFlow::class.java, MutableStateFlowAdapter())
            .setPrettyPrinting()
            .create()
    }

    /**
     * Преобразует список блоков в JSON-представление
     * @param blocks список блоков для сериализации
     * @return JSON-строка
     */
    fun blocksToJson(blocks: List<Block>): String {
        val type = object : TypeToken<List<Block>>() {}.type
        return try {
            gson.toJson(blocks, type)
        } catch (e: Exception) {
            e.printStackTrace()
            "[]" // Возвращаем пустой массив в случае ошибки
        }
    }

    /**
     * Преобразует JSON в список блоков
     * @param json строка JSON для десериализации 
     * @return список блоков или пустой список в случае ошибки
     */
    fun jsonToBlocks(json: String): List<Block> {
        if (json.isEmpty()) return emptyList()
        
        return try {
            gson.fromJson(json, object : TypeToken<List<Block>>() {}.type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
