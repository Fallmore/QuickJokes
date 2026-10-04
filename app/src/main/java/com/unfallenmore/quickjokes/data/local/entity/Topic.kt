package com.unfallenmore.quickjokes.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.unfallenmore.quickjokes.data.model.TopicType

/**
 * Тема шуток. Предусловленные темы имеют фиксированные отрицательные id
 * (из ассета), пользовательские — положительные, назначаются при создании
 * (см. domain/TopicIds.kt): автоинкремент Room у [id] не включён.
 *
 * Для IMAGE храним [imagePath] — путь к копии файла во внутреннем хранилище
 * приложения (копия делается при выборе, чтобы URI из галереи не «протух»).
 */
@Entity(
    tableName = "topics",
    indices = [Index("isCustom"), Index("isLocked")],
)
data class Topic(
    @PrimaryKey
    val id: Long = 0L,
    val title: String,
    val description: String = "",
    val type: TopicType = TopicType.TEXT,
    /** Относительный путь файла картинки во внутреннем хранилище (для IMAGE). */
    val imagePath: String? = null,
    /** Описание сцены (для SITUATION). */
    val situation: String? = null,
    val isCustom: Boolean = false,
    val isLocked: Boolean = false,
    /** Стоимость разблокировки в монетах (для закрытых тем). */
    val cost: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
