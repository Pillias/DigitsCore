package com.digitscore.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.digitscore.app.data.entity.AppWeightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM app_weights ORDER BY appName ASC")
    fun getAllAppWeights(): Flow<List<AppWeightEntity>>

    @Query("SELECT * FROM app_weights WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppWeight(packageName: String): AppWeightEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppWeights(appWeights: List<AppWeightEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAppWeight(appWeight: AppWeightEntity)

    @Update
    suspend fun updateAppWeight(appWeight: AppWeightEntity)
}
