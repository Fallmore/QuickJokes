package com.unfallenmore.quickjokes.data.local

import androidx.room.TypeConverter
import com.unfallenmore.quickjokes.data.model.AchievementType
import com.unfallenmore.quickjokes.data.model.CoinTransactionType
import com.unfallenmore.quickjokes.data.model.Difficulty
import com.unfallenmore.quickjokes.data.model.InputMode
import com.unfallenmore.quickjokes.data.model.SessionStatus
import com.unfallenmore.quickjokes.data.model.TopicType

/**
 * Enum'ы храним строками: значения перечислений стабильны,
 * а в SQL их можно читать/фильтровать без дополнительных маппингов.
 */
class Converters {

    @TypeConverter
    fun topicTypeToString(value: TopicType): String = value.name

    @TypeConverter
    fun stringToTopicType(value: String?): TopicType = TopicType.fromString(value)

    @TypeConverter
    fun difficultyToString(value: Difficulty): String = value.name

    @TypeConverter
    fun stringToDifficulty(value: String?): Difficulty = Difficulty.fromString(value)

    @TypeConverter
    fun inputModeToString(value: InputMode): String = value.name

    @TypeConverter
    fun stringToInputMode(value: String?): InputMode = InputMode.fromString(value)

    @TypeConverter
    fun sessionStatusToString(value: SessionStatus): String = value.name

    @TypeConverter
    fun stringToSessionStatus(value: String?): SessionStatus = SessionStatus.fromString(value)

    @TypeConverter
    fun coinTypeToString(value: CoinTransactionType): String = value.name

    @TypeConverter
    fun stringToCoinType(value: String?): CoinTransactionType =
        CoinTransactionType.fromString(value)

    @TypeConverter
    fun achievementTypeToString(value: AchievementType): String = value.name

    @TypeConverter
    fun stringToAchievementType(value: String?): AchievementType =
        AchievementType.fromString(value)
}
