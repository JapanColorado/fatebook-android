package dev.russell.fatebook.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OptionDao {

    @Query("SELECT * FROM options ORDER BY createdAtEpochMs ASC")
    fun observeAll(): Flow<List<OptionEntity>>

    @Query("SELECT * FROM options WHERE questionId = :questionId ORDER BY createdAtEpochMs ASC")
    suspend fun getByQuestionId(questionId: String): List<OptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(options: List<OptionEntity>)

    @Query("UPDATE options SET latestForecast = :forecast, latestForecastAtEpochMs = :forecastAtEpochMs WHERE id = :optionId")
    suspend fun updateLatestForecast(optionId: String, forecast: Double, forecastAtEpochMs: Long)

    @Query("UPDATE options SET resolution = :resolution, resolvedAtEpochMs = :resolvedAtEpochMs WHERE id = :optionId")
    suspend fun updateResolution(optionId: String, resolution: String?, resolvedAtEpochMs: Long?)

    @Query("DELETE FROM options WHERE questionId = :questionId")
    suspend fun deleteByQuestionId(questionId: String)

    @Query("DELETE FROM options")
    suspend fun deleteAll()
}
