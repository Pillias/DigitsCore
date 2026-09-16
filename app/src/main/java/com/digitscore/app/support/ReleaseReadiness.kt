package com.digitscore.app.support

enum class ReadinessStatus {
    COMPLETE,
    READY_TO_VALIDATE,
    OWNER_ACTION,
    EXTERNAL_VALIDATION
}

data class ReleaseReadinessItem(
    val number: String,
    val title: String,
    val detail: String,
    val status: ReadinessStatus
)

object ReleaseReadiness {
    val required = listOf(
        ReleaseReadinessItem("1", "최근 24시간 실제 언락 이벤트", "ACTION_USER_PRESENT와 KEYGUARD_HIDDEN 관측 이벤트만 집계하고 추정치는 사용하지 않습니다.", ReadinessStatus.COMPLETE),
        ReleaseReadinessItem("2", "UsageEvents 증분 처리", "프로세스 시작 시 상태를 한 번 복원한 뒤 마지막 커서 이후 이벤트만 조회합니다.", ReadinessStatus.COMPLETE),
        ReleaseReadinessItem("3", "Samsung·Pixel·Xiaomi 장기 측정", "기기별 2~4주 실측은 실제 기기와 측정 담당자가 필요합니다.", ReadinessStatus.EXTERNAL_VALIDATION),
        ReleaseReadinessItem("4", "정확도와 배터리 수치화", "포착률·조회시간·CPU 진단은 준비됐습니다. 장기 기기 시험에서 시간 오차와 배터리 값을 채워야 합니다.", ReadinessStatus.READY_TO_VALIDATE),
        ReleaseReadinessItem("5", "업로드 키와 서명 AAB", "서명 자동화는 준비됐지만 업로드 키 생성·보관 및 GitHub Secrets 입력이 필요합니다.", ReadinessStatus.OWNER_ACTION),
        ReleaseReadinessItem("6", "개인정보·지원·Data Safety", "개인정보처리방침 초안과 Data Safety 답안은 준비됐습니다. 개발자명·지원 이메일·공개 URL 확정이 필요합니다.", ReadinessStatus.OWNER_ACTION),
        ReleaseReadinessItem("7", "FGS 설명과 시연 영상", "제출 설명과 촬영 순서는 준비됐습니다. 실기기 화면 녹화와 Play Console 제출이 남았습니다.", ReadinessStatus.READY_TO_VALIDATE),
        ReleaseReadinessItem("8", "Android Vitals 감시", "내부 테스트 업로드 뒤 Play Console에서 Crash·ANR·wake lock을 실제 감시해야 합니다.", ReadinessStatus.EXTERNAL_VALIDATION),
        ReleaseReadinessItem("9", "접근성·화면 크기 시험", "테마·반응형 기반은 준비됐습니다. 큰 글꼴·TalkBack·가로·태블릿 수동 매트릭스 시험이 남았습니다.", ReadinessStatus.READY_TO_VALIDATE),
        ReleaseReadinessItem("10", "20~50명 30일 점수 검증", "동의한 실제 사용자를 모집해 개인 식별 없는 집계 결과를 검증해야 합니다.", ReadinessStatus.EXTERNAL_VALIDATION)
    )

    val product = listOf(
        ReleaseReadinessItem("P1", "점수 변화 이유 한 문장", "현재 지수 변화의 가장 큰 원인을 대시보드에 표시합니다.", ReadinessStatus.COMPLETE),
        ReleaseReadinessItem("P2", "예상 회복 시간", "화면을 쉬었을 때 목표 지수까지의 예상 시간을 표시합니다.", ReadinessStatus.COMPLETE),
        ReleaseReadinessItem("P3", "부드러운 임계치 알림", "프리셋별 관찰 시간의 점수 하락·화면 사용·연속 사용을 감지하며 사용자가 기준과 재알림 간격을 조정할 수 있습니다.", ReadinessStatus.COMPLETE),
        ReleaseReadinessItem("P4", "하루·주간 핵심 요약", "최근 24시간 사용과 최근 7일/이전 7일 비교를 제공합니다.", ReadinessStatus.COMPLETE),
        ReleaseReadinessItem("P5", "한 번에 한 가지 제안", "현재 흐름에서 실행할 한 가지 행동만 추천합니다.", ReadinessStatus.COMPLETE),
        ReleaseReadinessItem("P6", "사용자 자신의 과거와 비교", "고정 타인 평균 대신 사용자의 최근 기록을 기준선으로 사용합니다.", ReadinessStatus.COMPLETE)
    )

    val all: List<ReleaseReadinessItem> = required + product

    fun count(status: ReadinessStatus): Int = all.count { it.status == status }
}
