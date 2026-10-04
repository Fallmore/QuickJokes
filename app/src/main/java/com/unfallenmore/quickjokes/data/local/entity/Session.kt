package com.unfallenmore.quickjokes.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.unfallenmore.quickjokes.data.model.Difficulty
import com.unfallenmore.quickjokes.data.model.InputMode
import com.unfallenmore.quickjokes.data.model.SessionStatus

/**
 * Игровая сессия: один заход с таймером по одной теме.
 *
 * [startTime] и [endTime] — epoch millis; фактическая длительность
 * вычисляется как endTime - startTime, поэтому досрочный финиш и таймаут
 * не требуют отдельного подсчёта «тиканий».
 */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = Topic::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("topicId"), Index("startTime")],
)
data class Session(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val topicId: Long,
    val startTime: Long,
    val endTime: Long,
    /** Плановая длительность сессии в секундах. */
    val durationSec: Int,
    val difficulty: Difficulty,
    val inputMode: InputMode,
    val status: SessionStatus,
    val totalJokes: Int,
    /** Может быть null, если сессия прервана до прохождения порога. */
    @ColumnInfo(defaultValue = "0")
    val jokesPerMinute: Double = 0.0,
    val coinsEarned: Int = 0,
)
