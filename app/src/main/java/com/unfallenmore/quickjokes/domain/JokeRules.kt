package com.unfallenmore.quickjokes.domain

/**
 * Правила того, что считается шуткой.
 * Вынесено отдельно, чтобы правило можно было поменять в одном месте
 * (и покрыть тестами).
 */
object JokeRules {

    /** Минимальная длина текста шутки после обрезки пробелов. */
    const val MIN_LENGTH = 3

    /** Максимальная длина — защита от «простыней» в одном поле. */
    const val MAX_LENGTH = 500

    /** Нормализация: обрезка краёв и схлопывание пустых строк. */
    fun normalize(raw: String): String =
        raw.trim().replace(Regex("[ \\t]+"), " ")

    /** true, если строка может быть сохранена как шутка. */
    fun isValid(raw: String): Boolean {
        val text = normalize(raw)
        return text.length in MIN_LENGTH..MAX_LENGTH
    }

    /**
     * Шутка валидна, если это текст по правилам [isValid] **или**
     * голосовая запись ([hasAudio] — тогда текст может быть пустым).
     */
    fun isValid(raw: String, hasAudio: Boolean): Boolean =
        hasAudio || isValid(raw)

    /** Длительность голосовой записи, при которой она засчитывается (мс). */
    const val MIN_AUDIO_MS = 500L
}
