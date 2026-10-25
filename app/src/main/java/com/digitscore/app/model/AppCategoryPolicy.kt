package com.digitscore.app.model

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * 모드(PresetMode) 및 패키지 특성에 따른 기본 앱 등급 분류 정책
 * 추후 학생 모드, 직장인 모드 등 모드별 앱 성격 변경을 유연하게 처리합니다.
 */
object AppCategoryPolicy {

    private const val PREFS_NAME = "custom_study_packages_prefs"
    private const val KEY_STUDY_PACKAGES = "study_packages_set"
    private const val KEY_EXCLUDED_STUDY_PACKAGES = "excluded_study_packages_set"

    @Volatile
    private var cachedCustomStudyPackages: Set<String>? = null
    @Volatile
    private var cachedExcludedStudyPackages: Set<String>? = null

    fun getCustomStudyPackages(context: Context): Set<String> {
        val cached = cachedCustomStudyPackages
        if (cached != null) return cached
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_STUDY_PACKAGES, emptySet())?.toSet() ?: emptySet()
        cachedCustomStudyPackages = set
        return set
    }

    fun getExcludedStudyPackages(context: Context): Set<String> {
        val cached = cachedExcludedStudyPackages
        if (cached != null) return cached
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_EXCLUDED_STUDY_PACKAGES, emptySet())?.toSet() ?: emptySet()
        cachedExcludedStudyPackages = set
        return set
    }

    fun setCustomStudyPackage(context: Context, packageName: String, isStudy: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val studySet = prefs.getStringSet(KEY_STUDY_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
        val excludedSet = prefs.getStringSet(KEY_EXCLUDED_STUDY_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
        val pkg = packageName.lowercase()
        if (isStudy) {
            studySet.add(pkg)
            excludedSet.remove(pkg)
        } else {
            studySet.remove(pkg)
            if (EDUCATION_PACKAGES.any { pkg.startsWith(it) }) {
                excludedSet.add(pkg)
            }
        }
        prefs.edit()
            .putStringSet(KEY_STUDY_PACKAGES, studySet)
            .putStringSet(KEY_EXCLUDED_STUDY_PACKAGES, excludedSet)
            .apply()
        cachedCustomStudyPackages = studySet.toSet()
        cachedExcludedStudyPackages = excludedSet.toSet()
    }

    fun setStudyPackagesSets(context: Context, studyPackages: Set<String>, excludedPackages: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet(KEY_STUDY_PACKAGES, studyPackages)
            .putStringSet(KEY_EXCLUDED_STUDY_PACKAGES, excludedPackages)
            .apply()
        cachedCustomStudyPackages = studyPackages.toSet()
        cachedExcludedStudyPackages = excludedPackages.toSet()
    }


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

    // 통화 / 다이얼러 패키지
    val CALL_PACKAGES: Set<String> = setOf(
        "com.samsung.android.dialer",
        "com.google.android.dialer",
        "com.android.phone"
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

    // 학습 / 교육 / 어학 / 인강 / 독서·전자책 앱 (모든 모드 기본 EXEMPT 면제, 연속 50분 한도 대상)
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
        "com.megastudy.smartlearning",  // 메가스터디
        "com.selabs.speak",             // 스픽
        "kr.epopsoft.word",             // 말해보카
        "co.riiid.vida",                // 산타
        "kr.co.millie.millieshelf",     // 밀리의서재
        "com.initialcoms.ridi",         // 리디
        "com.kyobo.ebook",              // 교보eBook
        "com.yes24.ebook",              // 예스24 eBook
        "com.influential.youngkangapp", // 윌라
        "net.pedaling.class101"         // 클래스101
    )

    // 은행 / 페이 / 인증 / 메일 / 캘린더 / 문서 / 업무 협업툴 (기본 EXEMPT 면제, 연속 30분 한도 대상)
    val FINANCE_AND_WORK_PACKAGES: Set<String> = setOf(
        // 은행 / 페이 / 공공인증
        "viva.republica.toss",          // 토스
        "com.kakaobank.channel",        // 카카오뱅크
        "com.kbankwith.smartbank",      // 케이뱅크
        "com.kbstar.kbbank",            // KB스타뱅킹
        "com.shinhan.sbanking",         // 신한SOL
        "com.wooribank.smart.npib",     // 우리WON
        "com.kebhana.hanapush",         // 하나원큐
        "com.nh.mobilenoti",            // NH올원
        "com.samsung.android.spay",     // 삼성페이
        "com.naverfin.payapp",          // 네이버페이
        "com.kakaopay.app",             // 카카오페이
        "com.sktelecom.tauth",
        "com.kt.ktauth",
        "com.lguplus.smartotp",
        "kr.go.minwon.m",               // 정부24
        "kr.go.mobileid",               // 모바일신분증
        // 메일 / 캘린더 / 문서 / 업무 협업툴
        "com.google.android.gm",        // Gmail
        "com.microsoft.office.outlook", // Outlook
        "com.nhn.android.mail",         // 네이버메일
        "com.google.android.calendar",
        "com.samsung.android.calendar",
        "notion.id",                    // Notion
        "com.google.android.apps.docs", // Google Docs/Drive
        "com.microsoft.office",
        "com.microsoft.teams",          // Teams
        "com.slack",                    // Slack
        "com.tosslab.jandi.app",        // 잔디
        "us.zoom.videomeetings"         // Zoom
    )

    // 쇼핑 앱 (기본 등급: DISTRACTING 관리)
    val SHOPPING_PACKAGES: Set<String> = setOf(
        "com.alibaba.aliexpress",
        "com.amazon.mshop",
        "com.shopee",
        "com.ebay.mobile",
        "com.einnovation.temu",
        "com.contextlogic.wish",
        "com.coupang.mobile",
        "com.musinsa.store",            // 무신사
        "com.banhala.android",          // 에이블리
        "com.croquis.zigzag",           // 지그재그
        "com.the29cm.app29cm",          // 29CM
        "net.bucketplace",              // 오늘의집
        "com.towneers.www"              // 당근
    )

    // 웹툰 앱 (기본 등급: DISTRACTING 관리)
    val WEBTOON_PACKAGES: Set<String> = setOf(
        "com.nhn.android.webtoon",
        "com.kakao.page",
        "com.daum.android.webtoon",
        "com.nhn.android.nbooks"
    )

    // 메신저 앱 (기본 등급: NEUTRAL 일반)
    val MESSENGER_PACKAGES: Set<String> = setOf(
        "com.kakao.talk",
        "jp.naver.line.android",
        "org.telegram.messenger",
        "com.whatsapp",
        "com.facebook.orca",
        "com.discord",
        "com.tencent.mm",
        "org.thoughtcrime.securesms",
        "com.samsung.android.messaging",
        "com.google.android.apps.messaging"
    )

    // 소셜 미디어 / 숏폼 / 스트리밍 앱 (기본 등급: DISTRACTING 관리)
    val SOCIAL_MEDIA_PACKAGES: Set<String> = setOf(
        "com.instagram.android",
        "com.instagram.barcelona",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.twitter.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.google.android.youtube",
        "tv.twitch.android.app",
        "com.navercorp.game.android.community",
        "kr.co.nowcom.mobile.afreeca",
        "com.reddit.frontpage"
    )

    /**
     * 시간 제한 없는 순수 면제 앱(내비게이션, 통화 다이얼러)인지 판별합니다.
     */
    fun isNavigationOrCallPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return NAVIGATION_PACKAGES.any { pkg.startsWith(it) } || CALL_PACKAGES.any { pkg.startsWith(it) }
    }

    /**
     * 공부 / 어학 / 인강 / 독서·전자책 면제 앱(50분 면제 한도 대상)인지 판별합니다.
     */
    fun isStudyOrReadingPackage(packageName: String, context: Context? = null): Boolean {
        val pkg = packageName.lowercase()
        val excluded = if (context != null) getExcludedStudyPackages(context) else cachedExcludedStudyPackages
        if (excluded?.contains(pkg) == true) return false
        val custom = if (context != null) getCustomStudyPackages(context) else cachedCustomStudyPackages
        if (custom?.contains(pkg) == true) return true
        return EDUCATION_PACKAGES.any { pkg.startsWith(it) }
    }


    /** 화면 신호등(엣지 라이팅) 분류. 점수 계산과 같은 기준을 씁니다. */
    enum class EdgeKind { STUDY, MANAGED, NONE }

    /**
     * 사용자 지정이 반영된 실제 등급([effectiveCategoryLevel])으로 신호등 종류를 정합니다.
     * - 공부(초록): 면제 등급이면서 공부·독서 앱 (점수의 공부 면제와 동일 조건)
     * - 관리(빨강): 관리 등급 (기본 분류가 아니라 사용자가 정한 등급 기준)
     */
    fun edgeKindFor(packageName: String, effectiveCategoryLevel: Int, context: Context? = null): EdgeKind = when {
        effectiveCategoryLevel <= 0 && isStudyOrReadingPackage(packageName, context) -> EdgeKind.STUDY
        effectiveCategoryLevel >= 3 -> EdgeKind.MANAGED
        else -> EdgeKind.NONE
    }

    /**
     * 모드(PresetMode)에 따른 앱의 기본 등급(Category)을 결정합니다.
     */
    fun resolveDefaultCategory(
        presetMode: PresetMode = PresetMode.BALANCED,
        packageName: String,
        applicationCategory: Int
    ): AppCategoryType {
        val pkg = packageName.lowercase()

        // 1. 내비게이션 / 지도
        if (NAVIGATION_PACKAGES.any { pkg.startsWith(it) } || applicationCategory == ApplicationInfo.CATEGORY_MAPS) {
            if (presetMode == PresetMode.STUDY || presetMode == PresetMode.KIDS) {
                return AppCategoryType.NEUTRAL
            }
            return AppCategoryType.EXEMPT
        }

        // 2. 학습 / 교육 / 어학 / 독서 앱: 모든 모드에서 기본 EXEMPT (면제)
        if (EDUCATION_PACKAGES.any { pkg.startsWith(it) }) {
            return AppCategoryType.EXEMPT
        }

        // 3. 은행 / 페이 / 인증 / 업무 협업툴: 기본 EXEMPT (면제)
        if (FINANCE_AND_WORK_PACKAGES.any { pkg.startsWith(it) }) {
            return AppCategoryType.EXEMPT
        }

        // 4. 기본 시스템 유틸리티(전화, 계산기, 시계, 카메라 등): 모든 모드에서 EXEMPT (면제)
        if (UTILITY_PACKAGES.any { pkg.startsWith(it) }) {
            return AppCategoryType.EXEMPT
        }

        // 5. 소셜 미디어 / 숏폼 / 스트리밍: DISTRACTING (관리)
        if (SOCIAL_MEDIA_PACKAGES.any { pkg.startsWith(it) }) {
            return AppCategoryType.DISTRACTING
        }

        // 6. 웹툰 앱: DISTRACTING (관리)
        if (WEBTOON_PACKAGES.any { pkg.startsWith(it) }) {
            return AppCategoryType.DISTRACTING
        }

        // 7. 쇼핑 앱: DISTRACTING (관리)
        if (SHOPPING_PACKAGES.any { pkg.startsWith(it) }) {
            return AppCategoryType.DISTRACTING
        }

        // 8. 메신저 앱: NEUTRAL (일반)
        if (MESSENGER_PACKAGES.any { pkg.startsWith(it) }) {
            return AppCategoryType.NEUTRAL
        }

        // 9. Android ApplicationInfo Category 기반 기본 매핑
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
               EDUCATION_PACKAGES.any { pkg.startsWith(it) } ||
               FINANCE_AND_WORK_PACKAGES.any { pkg.startsWith(it) } ||
               SHOPPING_PACKAGES.any { pkg.startsWith(it) } ||
               WEBTOON_PACKAGES.any { pkg.startsWith(it) }
    }
}
