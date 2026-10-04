package com.unfallenmore.quickjokes.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unfallenmore.quickjokes.data.local.entity.Achievement
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {

    /** Каталог сидится при каждом запуске: новые коды добавляются, старые не трогаются. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(catalog: List<Achievement>)

    @Query("SELECT * FROM achievements ORDER BY sortOrder ASC")
    fun observeCatalog(): Flow<List<Achievement>>

    @Query("SELECT * FROM achievements ORDER BY sortOrder ASC")
    suspend fun getCatalog(): List<Achievement>
}
