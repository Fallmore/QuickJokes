package com.unfallenmore.quickjokes.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Отдельная шутка внутри сессии.
 * Каскадное удаление: при удалении сессии шутки уходят вместе с ней.
 */
@Entity(
    tableName = "jokes",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class Joke(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Порядок внутри сессии (1-based), чтобы сохранять порядок ввода. */
    val orderIndex: Int,
    /**
     * Относительный путь записи речи (диктофон) во внутреннем хранилище.
     * Для голосовых шуток text пуст — сама шутка lives в аудиофайле.
     */
    val audioPath: String? = null,
) {
    /** Шутка-запись: смысла в тексте нет, всё в аудио. */
    val hasAudio: Boolean
        get() = !audioPath.isNullOrBlank()
}
