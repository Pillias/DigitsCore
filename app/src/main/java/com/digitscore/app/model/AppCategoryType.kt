package com.digitscore.app.model

/**
 * 앱 사용이 디지털 균형에 미치는 정도를 나타내는 5단계 등급입니다.
 *
 * 기존 PRODUCTIVE / NEUTRAL / DISTRACTING 이름을 유지하여 이미 저장된 Room 데이터와
 * 백업 파일을 그대로 읽을 수 있게 하고, 중간 단계만 추가합니다.
 */
enum class AppCategoryType(
    val level: Int,
    val displayName: String,
    val description: String,
    val scoreMultiplier: Float
) {
    DISTRACTING(
        level = 1,
        displayName = "집중 방해",
        description = "사용 시간을 적극적으로 줄이고 싶은 앱",
        scoreMultiplier = -1.0f
    ),
    MILDLY_DISTRACTING(
        level = 2,
        displayName = "절제 권장",
        description = "조금 줄여 쓰면 균형에 도움이 되는 앱",
        scoreMultiplier = -0.5f
    ),
    NEUTRAL(
        level = 3,
        displayName = "균형",
        description = "점수에 보너스나 감점을 주지 않는 앱",
        scoreMultiplier = 0.0f
    ),
    MILDLY_PRODUCTIVE(
        level = 4,
        displayName = "도움",
        description = "목표 달성에 어느 정도 도움이 되는 앱",
        scoreMultiplier = 0.5f
    ),
    PRODUCTIVE(
        level = 5,
        displayName = "성장",
        description = "학습과 생산성에 직접 도움이 되는 앱",
        scoreMultiplier = 1.0f
    );

    val isPenalty: Boolean get() = scoreMultiplier < 0f
    val isBonus: Boolean get() = scoreMultiplier > 0f

    companion object {
        val orderedEntries: List<AppCategoryType> = entries.sortedBy { it.level }
    }
}
