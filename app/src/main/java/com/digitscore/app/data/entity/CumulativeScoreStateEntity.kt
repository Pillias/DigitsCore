package com.digitscore.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Small encrypted checkpoint; independent of the retention of raw usage details. */
@Entity(tableName = "cumulative_score_state")
data class CumulativeScoreStateEntity(
    @PrimaryKey val id: Int = 1,
    val payload: String
)
