package com.digitscore.app.i18n

import com.digitscore.app.support.ReleaseReadiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Locale

class UiTranslatorTest {
    @Test
    fun englishModeTranslatesStaticAndDynamicDashboardText() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("Today's App Usage", UiTranslator.translate("오늘의 앱 사용 현황"))
            val translated = UiTranslator.translate("최근 7일 평균 42분")
            assertFalse(translated.contains(Regex("[가-힣]")))

            val auditedUiTexts = listOf(
                "최근 24시간",
                "최근 30일",
                "사용시간 상위 6개 앱",
                "상위 7개 앱",
                "전체 12개 보기 ❯",
                "OS 감지 알림 14건 · 언락 20회",
                "알림 14건과 언락 20회 비교 그래프",
                "최근 30일 중 12일 측정",
                "최근 30일 사용량은 이전 기간과 비슷합니다.",
                "최근 30일 평균이 이전 기간보다 12% 늘었습니다.",
                "최근 30일 평균이 이전 기간보다 8% 줄었습니다.",
                "최근 30일에 새 사용 기록이 생겼습니다.",
                "평균 사용이 가장 많은 요일은 월요일 · 42분입니다.",
                "🌙 심야 사용 18분",
                "24시간 앱 사용량 그래프",
                "24시간 언락 횟수 그래프",
                "관리 대상 앱별 사용시간 그래프",
                "방해 생산성 중립 앱 사용시간 비교 그래프",
                "🌙 심야(24시~05시) 감점 배수: 1.5배",
                "방어선: 70점",
                "백업 파일은 20MB 이하여야 합니다.",
                "앱을 누르면 시간대·세션·최근 추세를 볼 수 있습니다.",
                "12개 앱 · 항목을 누르면 등급 설명과 변경 옵션을 볼 수 있습니다.",
                "상세 보기",
                "전체 보기",
                "관리 앱 상세 분석",
                "관리 대상 앱 목록입니다. 앱을 눌러 등급을 변경할 수 있습니다.",
                "성장 앱",
                "균형/기타",
                "검색 결과가 없습니다.",
                "생활 패턴에 맞는 기본값을 선택하고 아래에서 세부 조정합니다.",
                "상태바와 위젯의 표시 방식, 실시간 추적 여부를 관리합니다.",
                "코어 숫자형 (권장)",
                "전원 단계형",
                "큰 점수 크기는 유지하고, 점수 구간에 맞는 고대비 단색으로 숫자와 전원 원호를 표시합니다.",
                "개인 목표 기준선",
                "점수 안내와 언락 기준을 본인의 생활 패턴에 맞춥니다.",
                "세부 가중치 설정",
                "심야 사용, 장시간 사용, 회복과 언락의 반영 강도를 조정합니다.",
                "기록 보존, 암호화 백업, 복원과 삭제를 관리합니다.",
                "버전과 개인정보 처리 정책을 확인합니다.",
                "점선은 설정한 기준선 70점을 나타냅니다.",
                "하루 평균 화면 125분, 관리 앱 42분, 언락 31회입니다.",
                "기록된 7일 중 5일이 기준선 70점 이상이었습니다.",
                "화면 OFF 백그라운드 재생은 포함하지 않습니다.",
                "최근 24시간 언락 횟수는 코어 지수의 사용 부하에 완만하게 반영됩니다.",
                "일별 코어 지수 추세 (0~100)",
                "코어 지수 기록을 준비하고 있습니다.\n사용 흐름을 측정하면 날짜별 기록이 쌓입니다.",
                "이 기간에 계산된 코어 지수가 아직 없습니다.",
                "기록 준비 중",
                "코어 지수 프리셋",
                "최근 24시간 사용 흐름에서 중요하게 볼 항목을 선택합니다.",
                "프리셋 변경 즉시 최근 24시간 기록을 새 기준으로 다시 계산합니다.",
                "큰 테두리는 프리셋이 바뀐 날입니다.",
                "프리셋 변경:",
                "2026-09-11 · 하루 코어 지수",
                "최저 62점 · 최고 84점 · 마지막 73점",
                "이 날짜에는 하루 중 변화 기록이 없습니다. 세부 변화는 화면을 사용하는 동안 5분 단위로 저장됩니다.",
                "화면을 사용하는 동안 5분 단위의 최신 값을 저장합니다. 화면을 끄고 쉬면 지수가 회복되고, 다음 사용 시 계산된 값까지 선으로 이어집니다.",
                "그래프의 날짜를 누르면 하루 중 변화를 볼 수 있습니다.",
                "프리셋: Everyday Balance",
                "앱별 시작·종료 상세와 5분 단위 코어 지수 표본은 30일, 날짜별 집계는 365일 보관합니다.",
                "최근 24시간 코어 지수",
                "현재 코어 지수 · 24H",
                "현재 코어 지수 · 4W",
                "24시간 내 첫 기록 대비",
                "24H 범위 최저 52 · 최고 88",
                "4주 범위 최저 52 · 최고 88",
                "09-11 14:30 · 72점\n화면 18분 · 관리 7분 · 언락 4회 · YouTube",
                "2026-09-11 · 시작 68 · 마지막 74 · 최저 59 · 최고 81\n화면 3시간 2분 · 관리 51분 · 언락 34회",
                "차트를 누르거나 드래그해 시점별 기록을 확인하세요. 화면을 끄고 쉰 구간은 다음 회복값까지 선으로 이어지며 사용량은 0으로 표시됩니다.",
                "범위봉은 하루의 시작·마지막·최저·최고 코어 지수를, 아래 막대는 기록된 모든 날짜의 화면 사용을 표시합니다.",
                "날짜를 누르면 하루 중 5분 단위 변화를 확인할 수 있습니다.",
                "하락/관리",
                "7일 평균",
                "회복",
                "현재 73점 · 최저 61 · 최고 84",
                "최저 61점, 최고 84점이며 42개 구간을 표시합니다.",
                "화면을 끄고 쉬는 동안 연속 사용 부하가 줄어 코어 지수가 회복됩니다. 차트는 다음 사용 시 계산된 회복값까지 흐름을 이어 표시합니다.",
                "24시간 사용과 언락",
                "화면 3시간 20분 · 언락 31회",
                "청록색은 전체 전면 사용, 빨간색은 몰입 관리 앱의 전면·병렬 표시, 노란 점은 언락 횟수입니다.",
                "최근 24시간 1시간 15분 사용했습니다.",
                "YouTube 앱의 가장 긴 전면 사용 세션입니다.",
                "연속 언락 사이 평균 간격은 18분입니다.",
                "같은 기간 OS 감지 알림은 22건입니다.",
                "4주 사용 패턴",
                "화면을 얼마나 오래 쓰는지 주간·요일별로 비교합니다.",
                "주간 하루 평균",
                "이전 7일보다 하루 평균 12분 감소",
                "요일별 하루 평균",
                "최다 월 · 3시간 2분",
                "최근 7일의 하루 평균 화면시간이 이전 7일보다 12분 줄었습니다.",
                "주간 막대와 요일 막대는 사용 기록이 있는 날의 화면시간 평균입니다. 기록이 없는 날을 0분으로 채우지 않습니다.",
                "4주 사용 요약",
                "가장 많이 쓴 날",
                "하루 사용 최고",
                "관리 앱 비중",
                "전체 화면시간 중",
                "전체 화면시간 12시간 중 관리 앱을 3시간 사용했습니다.",
                "몰입 관리 앱이 전면 또는 병렬 화면에 표시된 사용시간 비율입니다.",
                "지수가 움직인 이유",
                "YouTube 사용이 최근 부하의 가장 큰 원인입니다.",
                "지금 화면을 쉬면 약 20분 뒤 76점에 도달할 것으로 예상됩니다.",
                "화면 2시간 · 앱 14회 실행 · 1분 미만 9회",
                "최근 7일 평균 74점 · 이전 7일 대비 -2점",
                "증분 수집 · 전면 앱 포착률 96%",
                "최근 조회 62초 · 이벤트 17개 · 4ms",
                "오늘 31회 측정 · 조회 52ms · CPU 310ms",
                "실제 이벤트 기준 최근 24시간 언락 28회",
                "하루에 몇 번 열었나요?",
                "최근 14일 앱 실행 횟수와 1분 미만 실행 그래프",
                "오늘 14회 · 1분 미만 9회",
                "짧은 확인이 전체 실행의 64%입니다. 습관적으로 여는 흐름인지 살펴보세요."
            )
            auditedUiTexts.forEach { source ->
                val english = UiTranslator.translate(source)
                assertFalse("Untranslated Korean remains in: $english", english.contains(Regex("[가-힣]")))
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun koreanModeKeepsOriginalText() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.KOREAN)
            assertEquals("코어 지수", UiTranslator.translate("코어 지수"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun englishModeDoesNotRewriteKoreanAppNames() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("당근", UiTranslator.translate("당근"))
            assertEquals("삼성 브라우저", UiTranslator.translate("삼성 브라우저"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun englishModeFullyTranslatesSupportAndReadinessUi() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            val supportTexts = listOf(
                "버그 리포트",
                "출시 체크리스트",
                "출시 준비 체크리스트",
                "문제가 생긴 상황과 재현 순서를 적어주세요. 아래 보고서를 확인한 뒤 직접 공유합니다.",
                "문제 상황과 재현 순서",
                "예: 위젯이 오전 10시 이후 갱신되지 않음",
                "첨부되는 진단 정보",
                "앱 버전·기기 모델·Android 버전·권한 상태·측정 성능만 포함합니다. 앱 목록, 사용 이력, 점수 기록, 알림 내용, 계정 및 기기 식별자는 포함하지 않습니다.",
                "리포트 복사",
                "공유",
                "완료 8/16 · 검증 준비 3 · 관리자 입력 2 · 외부 검증 3",
                "반드시 필요한 것",
                "상품성을 위해 필요한 것",
                "기기·사용자·Play Console이 필요한 항목은 코드만으로 완료 처리하지 않습니다. 저장소 docs/RELEASE_READINESS.md에 실행 절차와 증빙 위치를 정리했습니다."
            ) + ReleaseReadiness.all.flatMap { listOf(it.title, it.detail) } +
                ReleaseReadiness.all.map {
                    when (it.status) {
                        com.digitscore.app.support.ReadinessStatus.COMPLETE -> "완료"
                        com.digitscore.app.support.ReadinessStatus.READY_TO_VALIDATE -> "검증 준비"
                        com.digitscore.app.support.ReadinessStatus.OWNER_ACTION -> "관리자 입력"
                        com.digitscore.app.support.ReadinessStatus.EXTERNAL_VALIDATION -> "외부 검증"
                    }
                }

            supportTexts.forEach { source ->
                val english = UiTranslator.translate(source)
                assertFalse("Untranslated Korean remains in: $english", english.contains(Regex("[가-힣]")))
            }
        } finally {
            Locale.setDefault(previous)
        }
    }
}
