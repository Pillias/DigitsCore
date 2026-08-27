package com.digitscore.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.digitscore.app.model.AppCategoryType

/**
 * 사용자별 / 패키지별 카테고리 및 가중치 매핑 엔티티
 */
@Entity(tableName = "app_weights")
data class AppWeightEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val categoryType: AppCategoryType,
    val customWeight: Float? = null,
    val isUserModified: Boolean = false
)
