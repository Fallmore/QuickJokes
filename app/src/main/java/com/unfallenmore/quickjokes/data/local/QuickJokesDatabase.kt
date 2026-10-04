package com.unfallenmore.quickjokes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.unfallenmore.quickjokes.data.local.dao.AchievementDao
import com.unfallenmore.quickjokes.data.local.dao.BackupDao
import com.unfallenmore.quickjokes.data.local.dao.ProfileDao
import com.unfallenmore.quickjokes.data.local.dao.SessionDao
import com.unfallenmore.quickjokes.data.local.dao.StatsDao
import com.unfallenmore.quickjokes.data.local.dao.TopicDao
import com.unfallenmore.quickjokes.data.local.entity.Achievement
import com.unfallenmore.quickjokes.data.local.entity.CoinTransaction
import com.unfallenmore.quickjokes.data.local.entity.Joke
import com.unfallenmore.quickjokes.data.local.entity.LoginDay
import com.unfallenmore.quickjokes.data.local.entity.Session
import com.unfallenmore.quickjokes.data.local.entity.Topic
import com.unfallenmore.quickjokes.data.local.entity.UserAchievement
import com.unfallenmore.quickjokes.data.local.entity.UserProfile

@Database(
    entities = [
        Topic::class,
        Session::class,
        Joke::class,
        Achievement::class,
        UserAchievement::class,
        CoinTransaction::class,
        UserProfile::class,
        LoginDay::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class QuickJokesDatabase : RoomDatabase() {
    abstract fun topicDao(): TopicDao
    abstract fun sessionDao(): SessionDao
    abstract fun statsDao(): StatsDao
    abstract fun profileDao(): ProfileDao
    abstract fun achievementDao(): AchievementDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "quickjokes.db"
    }
}
