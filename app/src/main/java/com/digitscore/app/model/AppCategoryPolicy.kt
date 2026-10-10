package com.digitscore.app.model

import android.content.pm.ApplicationInfo

/**
 * 모드(PresetMode) 및 패키지 특성에 따른 기본 앱 등급 분류 정책
 * 추후 학생 모드, 직장인 모드 등 모드별 앱 성격 변경을 유연하게 처리합니다.
 */
object AppCategoryPolicy {

    // 지도 / 차량 내비게이션 관련 패키지
    val NAVIGATION_PACKAGES: Set<String> = setOf(
        "com.skt.tmap.ku",              // TMAP
        "com.locnall.KimGiSa",          // 카카오내비
        "com.nhn.android.nmap",         // 네이버지도 / 네비
        "net.daum.android.map",         // 카카오맵
        "com.google.android.apps.maps", // Google 지도
        "com.waze",                     // Waze
        "com.mnsoft.mappy",             // 맵피
        "com.kt.ollehmap",              // 원내비
        "com.uplus.uplusnavi"           // U+내비
    )

    // 필수 시스템 유틸리티 / 통화 / 도구
    val UTILITY_PACKAGES: Set<String> = setOf(
        "com.samsung.android.dialer",
        "com.google.android.dialer",
        "com.android.phone",
        "com.sec.android.app.clockpackage",
        "com.google.android.deskclock",
        "com.sec.android.app.popupcalculator",
        "com.google.android.calculator",
        "com.sec.android.app.camera",
        "com.google.android.GoogleCamera",
        "com.sec.android.gallery3d",
        "com.google.android.apps.photos",
        "com.android.settings"
    )

    // 학습 / 교육 관련 앱
    val EDUCATION_PACKAGES: Set<String> = setOf(
        "com.duolingo",
        "com.ichi2.anki",
        "com.ankiandroid",
        "org.khanacademy",
        "com.coursera",
        "com.udemy",
        "com.quizlet",
        "com.google.android.apps.classroom",
        "org.edx",
        "com.mathpresso.qanda",         // 콴다
        "kr.co.ebs.ebsi",               // EBS
        "kr.co.ebs.primary",            // EBS 초등
        "kr.co.ebs.mid",                // EBS 중학
        "com.megastudy.smartlearning"   // 메가스터디
    )

    // 쇼핑 앱
    val SHOPPING_PACKAGES: Set<String> = setOf(
        "com.alibaba.aliexpress",
        "com.amazon.mshop",
        "com.shopee",
        "com.ebay.mobile",
        "com.einnovation.temu",
        "com.contextlogic.wish",
        "com.coupang.mobile"
    )

    /**
     * 모드(PresetMode)에 따른 앱의 기본 등급(Category)을 결정합니다.
     */
    fun resolveDefaultCategory(
        presetMode: PresetMode = PresetMode.BALANCED,
        packageName: String,
        applicationCategory: Int
    ): AppCategoryType {
        val pkg = packageName.lowercase()

        // 1. 모드별 특화 규칙
        when (presetMode) {
            PresetMode.STUDY, PresetMode.KIDS -> {
                // 학생 / 어린이 모드: 학습·인강 앱은 면제(도구) 또는 일반으로 적극 지원
                if (EDUCATION_PACKAGES.any { pkg.startsWith(it) }) {
                    return AppCategoryType.EXEMPT
                }
                // 학생 모드에서는 운전 내비가 성인처럼 필수 도구가 아니므로 일반(NEUTRAL)으로 취급
                if (NAVIGATION_PACKAGES.any { pkg.startsWith(it) } || applicationCategory == ApplicationInfo.CATEGORY_MAPS) {
                    return AppCategoryType.NEUTRAL
                }
            }
            else -> {
                // 성인 / 직장인 / 기본 모드:
                // 지도 / 내비게이션은 운전 및 안전 이동을 위한 필수 도구이므로 완전 면제(EXEMPT)
                if (NAVIGATION_PACKAGES.any { pkg.startsWith(it) } || applicationCategory == ApplicationInfo.CATEGORY_MAPS) {
                    return AppCategoryType.EXEMPT
                }
                if (EDUCATION_PACKAGES.any { pkg.startsWith(it) }) {
                    return AppCategoryType.NEUTRAL
                }
            }
        }

        // 2. 기본 유틸리티(전화, 계산기, 시계, 카메라 등)는 모든 모드에서 면제
        if (UTILITY_PACKAGES.any { pkg.startsWith(it) }) {
            return AppCategoryType.EXEMPT
        }

        // 3. 쇼핑 앱은 일반
        if (SHOPPING_PACKAGES.any { pkg.startsWith(it) }) {
            return AppCategoryType.NEUTRAL
        }

        // 4. Android ApplicationInfo Category 기반 기본 매핑
        return when (applicationCategory) {
            ApplicationInfo.CATEGORY_GAME,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategoryType.DISTRACTING

            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategoryType.NEUTRAL
            else -> AppCategoryType.NEUTRAL
        }
    }

    /**
     * 상단에 하이라이트(우선 조정 대상)로 표시할 앱인지 판별합니다.
     */
    fun isHighlightCandidate(
        packageName: String,
        categoryType: AppCategoryType,
        usageMinutes: Long = 0L
    ): Boolean {
        if (usageMinutes >= 15L) return true
        if (categoryType == AppCategoryType.DISTRACTING || categoryType == AppCategoryType.EXEMPT) return true
        val pkg = packageName.lowercase()
        return NAVIGATION_PACKAGES.any { pkg.startsWith(it) } ||
               EDUCATION_PACKAGES.any { pkg.startsWith(it) }
    }
}
