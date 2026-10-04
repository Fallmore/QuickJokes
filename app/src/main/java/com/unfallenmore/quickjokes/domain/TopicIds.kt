package com.unfallenmore.quickjokes.domain

/**
 * Правила назначения id тем.
 *
 * Предустановленные темы живут в отрицательных id (из ассета, чтобы IGNORE
 * при сиде не перезаписывал строки), пользовательские — в положительных.
 * Автоинкремент Room здесь не используется: у Topic id не autoGenerate,
 * поэтому новому id присваивается max(существующих) + 1 — коллизия
 * невозможна, а удалённые id не переиспользуются.
 */
object TopicIds {

    /** id=0 считается «не заданным» (значение по умолчанию поля id). */
    private const val MIN_CUSTOM_ID = 1L

    /**
     * Следующий свободный положительный id для собственной темы.
     *
     * @param maxExistingId MAX(id) по таблице тем (null — таблица пуста)
     */
    fun nextCustomId(maxExistingId: Long?): Long {
        val max = maxExistingId ?: 0L
        // max < 1: таблица пуста, в ней только предустановленные темы (< 0)
        // или легаси-строка с id = 0
        return if (max < MIN_CUSTOM_ID) MIN_CUSTOM_ID else max + 1L
    }
}
