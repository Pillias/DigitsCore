package com.digitscore.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.digitscore.app.data.entity.DeviceInteractionEventEntity

@Dao
interface DeviceInteractionEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<DeviceInteractionEventEntity>)

    @Query(
        """SELECT * FROM device_interaction_events
           WHERE timestampMillis >= :startMillis AND timestampMillis <= :endMillis
           ORDER BY timestampMillis ASC"""
    )
    suspend fun getBetween(startMillis: Long, endMillis: Long): List<DeviceInteractionEventEntity>

    @Query("DELETE FROM device_interaction_events WHERE timestampMillis < :cutoffMillis")
    suspend fun pruneBefore(cutoffMillis: Long)

    @Query("DELETE FROM device_interaction_events")
    suspend fun deleteAll()
}
