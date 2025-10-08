package com.pasich.encly.dynamicBlocks

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Интерфейс для блоков, содержащих текст
 * Используется для единообразной обработки текстовых блоков разных типов
 */
interface TextualBlock {
    val text: MutableStateFlow<String>
    
    /**
     * Проверяет пуст ли текст в блоке
     */
    fun isEmpty(): Boolean = text.value.isEmpty()
    
    /**
     * Добавляет текст в конец
     */
    fun appendText(addition: String) {
        text.value = text.value + addition
    }
}
