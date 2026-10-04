package com.unfallenmore.quickjokes.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.unfallenmore.quickjokes.data.local.QuickJokesDatabase
import com.unfallenmore.quickjokes.data.local.dao.ProfileDao
import com.unfallenmore.quickjokes.data.local.dao.SessionDao
import com.unfallenmore.quickjokes.data.local.dao.StatsDao
import com.unfallenmore.quickjokes.data.local.dao.TopicDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** Этап 9: к шутке добавлен путь её голосовой записи. */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE jokes ADD COLUMN audioPath TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): QuickJokesDatabase =
        Room.databaseBuilder(context, QuickJokesDatabase::class.java, QuickJokesDatabase.NAME)
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideTopicDao(db: QuickJokesDatabase): TopicDao = db.topicDao()

    @Provides
    fun provideSessionDao(db: QuickJokesDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideStatsDao(db: QuickJokesDatabase): StatsDao = db.statsDao()

    @Provides
    fun provideProfileDao(db: QuickJokesDatabase): ProfileDao = db.profileDao()
}
