package dev.russell.fatebook.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ForecastDao {

    @Query("SELECT * FROM forecasts")
    fun observeAll(): Flow<List<ForecastEntity>>

    @Query("SELECT * FROM forecasts WHERE questionId = :questionId ORDER BY createdAtEpochMs ASC")
    suspend fun getByQuestionId(questionId: String): List<ForecastEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(forecasts: List<ForecastEntity>)

    @Query("DELETE FROM forecasts WHERE questionId = :questionId")
    suspend fun deleteByQuestionId(questionId: String)

    @Query("DELETE FROM forecasts")
    suspend fun deleteAll()
}
