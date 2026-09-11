package com.digitscore.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 화면이 켜진 동안 5분 단위로 보존하는 최근 30일 코어 지수 변화 표본입니다. */
@Entity(
    tableName = "core_index_samples",
    indices = [Index("dateString"), Index("timestampMillis")]
)
data class CoreIndexSampleEntity(
    @PrimaryKey
    val bucketStartTimestamp: Long,
    val timestampMillis: Long,
    val dateString: String,
    val score: Int,
    val exactScore: Double,
    val rollingLoad: Double,
    val acuteLoad: Double,
    val presetId: String
)
