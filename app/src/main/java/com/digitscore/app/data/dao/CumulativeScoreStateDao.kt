package com.digitscore.app.data.dao

import androidx.room.*
import com.digitscore.app.data.entity.CumulativeScoreStateEntity

@Dao
interface CumulativeScoreStateDao {
    @Query("SELECT * FROM cumulative_score_state WHERE id = 1")
    suspend fun get(): CumulativeScoreStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(state: CumulativeScoreStateEntity)

    @Query("DELETE FROM cumulative_score_state")
    suspend fun deleteAll()
}
