package com.digitscore.app.model

import com.digitscore.app.data.entity.AppWeightEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.9.47 앱 등급 고정(C)과 등급 변경 요청(B). */
class GradeLockAndRequestTest {

    @After fun restoreSwitch() {
        AppCategoryPolicy.userGradesLocked = AppCategoryPolicy.USER_GRADES_LOCKED_DEFAULT
    }

    private val weights = listOf(
        AppWeightEntity("com.google.android.youtube", "YouTube", AppCategoryType.NEUTRAL, isUserModified = true),
        AppWeightEntity("com.netflix.mediaclient", "Netflix", AppCategoryType.DISTRACTING, isUserModified = false)
    )

    @Test fun lockIsOnByDefault() {
        assertTrue(AppCategoryPolicy.USER_GRADES_LOCKED_DEFAULT)
        assertTrue(AppCategoryPolicy.userGradesLocked)
    }

    @Test fun lockedIgnoresUserModifiedGrades() {
        AppCategoryPolicy.userGradesLocked = true
        val effective = AppCategoryPolicy.effectiveSavedWeights(weights) { it.isUserModified }
        assertEquals(listOf("com.netflix.mediaclient"), effective.map { it.packageName })
    }

    @Test fun unlockingRestoresPreviousBehavior() {
        AppCategoryPolicy.userGradesLocked = false
        val effective = AppCategoryPolicy.effectiveSavedWeights(weights) { it.isUserModified }
        assertEquals(weights, effective)
    }

    @Test fun lockedStudyAppsUseDefaultListOnly() {
        AppCategoryPolicy.userGradesLocked = true
        assertTrue(AppCategoryPolicy.isStudyOrReadingPackage("com.duolingo"))
        assertFalse(AppCategoryPolicy.isStudyOrReadingPackage("com.google.android.youtube"))
    }

    @Test fun requestMailHasOnlyAppAndGradeInfo() {
        val ko = GradeChangeRequest.build("YouTube", "com.google.android.youtube", AppGrade.LIMIT, AppGrade.DAILY, "0.9.47", english = false)
        assertEquals("[DigitsCore] 등급 변경 요청: YouTube", ko.subject)
        listOf("YouTube", "com.google.android.youtube", "지금 등급: 관리", "바라는 등급: 일반", "0.9.47", "이유:")
            .forEach { assertTrue("missing $it", ko.body.contains(it)) }
        // 사용 시간·점수·기기 정보는 넣지 않습니다.
        listOf("min", "P", "분", "점", "Android", "SDK").forEach { assertFalse("leaks $it", ko.body.contains(it)) }

        val en = GradeChangeRequest.build("Kindle", "com.amazon.kindle", AppGrade.DAILY, AppGrade.FOCUS, "0.9.47", english = true)
        assertEquals("[DigitsCore] Grade change request: Kindle", en.subject)
        assertTrue(en.body.contains("Current grade: Daily"))
        assertTrue(en.body.contains("Requested grade: Study"))
    }

    @Test fun blankAppNameFallsBackToPackage() {
        val msg = GradeChangeRequest.build("", "com.example.app", AppGrade.DAILY, AppGrade.LIMIT, "0.9.47", english = false)
        assertTrue(msg.subject.endsWith("com.example.app"))
    }
}
