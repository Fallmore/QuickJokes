package com.unfallenmore.quickjokes.navigation

import com.unfallenmore.quickjokes.data.model.Difficulty
import com.unfallenmore.quickjokes.data.model.InputMode

/**
 * Маршруты навигации. Аргументы кодируются прямо в строку маршрута.
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"

    const val TOPICS = "topics"

    const val TOPIC_EDITOR = "topic_editor?topicId={topicId}"
    fun topicEditor(topicId: Long? = null) =
        if (topicId == null) "topic_editor" else "topic_editor?topicId=$topicId"

    const val SESSION_SETUP = "session_setup/{topicId}"
    fun sessionSetup(topicId: Long) = "session_setup/$topicId"

    /** Сессия получает настройки аргументами — они же попадают в SavedStateHandle. */
    const val SESSION =
        "session/{topicId}?durationSec={durationSec}&difficulty={difficulty}&inputMode={inputMode}"
    fun session(
        topicId: Long,
        durationSec: Int,
        difficulty: Difficulty,
        inputMode: InputMode,
    ) = "session/$topicId?durationSec=$durationSec&difficulty=${difficulty.name}&inputMode=${inputMode.name}"

    const val RESULTS = "results/{sessionId}"
    fun results(sessionId: Long) = "results/$sessionId"

    const val HISTORY = "history"
    const val SESSION_DETAILS = "session_details/{sessionId}"
    fun sessionDetails(sessionId: Long) = "session_details/$sessionId"

    const val STATS = "stats"
    const val ACHIEVEMENTS = "achievements"
    const val SHOP = "shop"
    const val MY_TOPICS = "my_topics"
    const val SETTINGS = "settings"

    /** Маршруты, на которых показывается нижняя навигация. */
    val topLevel = setOf(HOME, HISTORY, STATS, ACHIEVEMENTS)
}
