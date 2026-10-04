package com.unfallenmore.quickjokes.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.unfallenmore.quickjokes.data.local.entity.Topic
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {

    @Query("SELECT * FROM topics ORDER BY isCustom ASC, title ASC")
    fun observeAll(): Flow<List<Topic>>

    @Query("SELECT * FROM topics WHERE isCustom = :isCustom ORDER BY title ASC")
    fun observeByCustom(isCustom: Boolean): Flow<List<Topic>>

    @Query("SELECT * FROM topics WHERE id = :id")
    fun observeById(id: Long): Flow<Topic?>

    @Query("SELECT * FROM topics WHERE id = :id")
    suspend fun getById(id: Long): Topic?

    @Query("SELECT * FROM topics")
    suspend fun getAll(): List<Topic>

    @Query("SELECT COUNT(*) FROM topics WHERE isCustom = 1")
    suspend fun countCustom(): Int

    /** Максимальный id в таблице (null — таблица пуста) — для новой собственной темы. */
    @Query("SELECT MAX(id) FROM topics")
    suspend fun maxId(): Long?

    /** Идемпотентный сид предустановленных тем: фиксированные id не перезаписываются. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(topics: List<Topic>)

    /**
     * Создание собственной темы. ABORT, а не REPLACE: при коллизии id
     * ошибка лучше, чем молчаливое затирание существующей темы.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(topic: Topic): Long

    @Update
    suspend fun update(topic: Topic)

    @Delete
    suspend fun delete(topic: Topic)

    @Query("DELETE FROM topics WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE topics SET isLocked = 0 WHERE id = :id")
    suspend fun unlock(id: Long)
}
