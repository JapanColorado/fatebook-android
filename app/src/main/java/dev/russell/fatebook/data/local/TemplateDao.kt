package dev.russell.fatebook.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    @Query("SELECT * FROM templates ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Insert
    suspend fun insert(template: TemplateEntity): Long

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteById(id: Long)
}
