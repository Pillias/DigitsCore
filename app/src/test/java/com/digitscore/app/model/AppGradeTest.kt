package com.digitscore.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppGradeTest {
    @Test fun roundTripsBetweenGradeAndCategory() {
        AppGrade.entries.forEach { grade ->
            assertEquals(grade, AppGrade.from(grade.category, grade.isStudy))
        }
        assertEquals(AppGrade.DAILY, AppGrade.from(AppCategoryType.PRODUCTIVE, isStudy = true))
    }

    @Test fun exemptDescriptionsMentionLimits() {
        assertTrue(AppGrade.FOCUS.description.contains("50"))
        assertTrue(AppGrade.TOOL.description.contains("30"))
    }

    @Test fun edgeKindFollowsUserGradeNotDefaultCategory() {
        // YouTube를 사용자가 '일반'(level 2)으로 바꾸면 빨간 신호등 대상이 아닙니다.
        assertEquals(AppCategoryPolicy.EdgeKind.NONE, AppCategoryPolicy.edgeKindFor("com.google.android.youtube", 2))
        assertEquals(AppCategoryPolicy.EdgeKind.MANAGED, AppCategoryPolicy.edgeKindFor("com.google.android.youtube", 3))
        // 공부 앱이라도 '관리'로 바꾸면 초록불이 아니라 관리 대상입니다.
        assertEquals(AppCategoryPolicy.EdgeKind.MANAGED, AppCategoryPolicy.edgeKindFor("com.duolingo", 3))
        assertEquals(AppCategoryPolicy.EdgeKind.STUDY, AppCategoryPolicy.edgeKindFor("com.duolingo", 0))
    }

    @Test
    fun `english-market and mixed-case packages get default grades`() {
        fun grade(pkg: String) = AppCategoryPolicy.resolveDefaultCategory(packageName = pkg, applicationCategory = -1)
        assertEquals(AppCategoryType.EXEMPT, grade("com.amazon.kindle"))
        assertEquals(AppCategoryType.EXEMPT, grade("com.chase.sig.android"))
        assertEquals(AppCategoryType.EXEMPT, grade("com.atlassian.android.jira.core"))
        assertEquals(AppCategoryType.DISTRACTING, grade("com.walmart.android"))
        // 대문자가 섞인 패키지명도 일치해야 합니다.
        assertEquals(AppCategoryType.EXEMPT, grade("com.locnall.KimGiSa"))
    }
}
