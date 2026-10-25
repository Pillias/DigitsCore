package com.digitscore.app.data

import android.content.Context
import com.digitscore.app.data.entity.AppWeightEntity
import com.digitscore.app.model.AppCategoryPolicy
import com.digitscore.app.model.AppGrade
import com.digitscore.app.service.TrackerForegroundService

/**
 * 앱 등급 변경의 단일 진입점입니다. 점수용 등급(DB)과 공부 앱 목록(신호등·면제 한도)을
 * 항상 함께 갱신해 화면마다 결과가 달라지지 않게 합니다.
 */
object AppGradeStore {
    suspend fun apply(context: Context, packageName: String, appName: String, grade: AppGrade) {
        val appContext = context.applicationContext
        AppCategoryPolicy.setCustomStudyPackage(appContext, packageName, grade.isStudy)
        DigitsDatabase.getInstance(appContext).appDao().insertOrUpdateAppWeight(
            AppWeightEntity(
                packageName = packageName,
                appName = appName,
                categoryType = grade.category,
                isUserModified = true
            )
        )
        // 추적 중일 때만 즉시 재계산합니다. 꺼져 있으면 서비스를 새로 시작하지 않습니다.
        TrackerForegroundService.refreshNotification(appContext)
    }
}
