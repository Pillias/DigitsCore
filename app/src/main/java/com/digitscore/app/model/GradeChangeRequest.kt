package com.digitscore.app.model

/**
 * 등급 변경 요청 메일 내용 (v0.9.47). 인터넷 권한 없이, 사용자가 메일 앱에서 내용을 보고 직접 보냅니다.
 * 앱 이름·패키지·지금 등급·바라는 등급·앱 버전만 넣습니다. 사용 시간·점수·기기 정보는 넣지 않습니다.
 */
object GradeChangeRequest {
    const val EMAIL = "DigitsCore@gmail.com"

    data class Message(val subject: String, val body: String)

    fun build(
        appName: String,
        packageName: String,
        current: AppGrade,
        desired: AppGrade,
        appVersion: String,
        english: Boolean
    ): Message {
        val name = appName.ifBlank { packageName }
        return if (english) Message(
            subject = "[DigitsCore] Grade change request: $name",
            body = buildString {
                appendLine("App: $name")
                appendLine("Package: $packageName")
                appendLine("Current grade: ${current.label(true)}")
                appendLine("Requested grade: ${desired.label(true)}")
                appendLine("DigitsCore version: $appVersion")
                appendLine()
                appendLine("Reason:")
                appendLine()
            }
        ) else Message(
            subject = "[DigitsCore] 등급 변경 요청: $name",
            body = buildString {
                appendLine("앱: $name")
                appendLine("패키지: $packageName")
                appendLine("지금 등급: ${current.label(false)}")
                appendLine("바라는 등급: ${desired.label(false)}")
                appendLine("DigitsCore 버전: $appVersion")
                appendLine()
                appendLine("이유:")
                appendLine()
            }
        )
    }

    /** 버튼 아래 설명 (PHASE2_WORK_SPEC B.3). */
    fun explanation(english: Boolean): String =
        if (english) "Tell us if this app's default grade looks wrong. Your mail app opens, and you can check the message before sending."
        else "이 앱의 기본 등급이 맞지 않으면 알려 주세요. 메일 앱이 열리고, 보내기 전에 내용을 확인할 수 있습니다."

    fun buttonLabel(english: Boolean): String = if (english) "Request a grade change" else "등급 변경 요청"
}
