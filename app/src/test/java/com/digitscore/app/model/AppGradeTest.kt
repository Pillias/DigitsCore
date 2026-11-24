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

    @Test
    fun `popular apps get intended grades without relying on the app's own category`() {
        fun grade(pkg: String) = AppCategoryPolicy.resolveDefaultCategory(packageName = pkg, applicationCategory = -1)
        // 동영상 스트리밍은 앱이 동영상 분류를 밝히지 않아도 관리입니다(실사용 백업에서 Netflix가 일반으로 계산됨).
        listOf(
            "com.netflix.mediaclient", "net.cj.cjhv.gs.tving", "kr.co.captv.pooqV2",
            "com.disney.disneyplus", "com.frograms.wplay", "com.coupang.mobile.play",
            "com.snapchat.android", "com.pinterest", "com.naver.linewebtoon",
            "com.elevenst", "com.ebay.kr.gmarket", "kr.co.ssg"
        ).forEach { assertEquals(it, AppCategoryType.DISTRACTING, grade(it)) }
        // 동영상 앱은 등급 화면에서 '소셜·메신저'가 아니라 '동영상·미디어'로 묶이도록 따로 둡니다.
        AppCategoryPolicy.VIDEO_STREAMING_PACKAGES.forEach { ott ->
            assertTrue(ott, AppCategoryPolicy.SOCIAL_MEDIA_PACKAGES.none { it.equals(ott, ignoreCase = true) })
        }
        // Android Auto는 내비게이션과 같이 시간 제한 없는 면제입니다.
        assertEquals(AppCategoryType.EXEMPT, grade("com.google.android.projection.gearhead"))
        assertTrue(AppCategoryPolicy.isNavigationOrCallPackage("com.google.android.projection.gearhead"))
        // 메신저와 음악은 일반입니다.
        assertEquals(AppCategoryType.NEUTRAL, grade("com.viber.voip"))
        assertEquals(AppCategoryType.NEUTRAL, grade("com.google.android.apps.youtube.music"))
    }
}
