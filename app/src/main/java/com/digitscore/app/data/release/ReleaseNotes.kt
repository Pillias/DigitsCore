package com.digitscore.app.data.release

import android.content.Context

/**
 * 앱 안 '업데이트 기록'. docs/release-notes/vX.Y.Z.md를 빌드 때 assets/release_notes/로 담아 읽습니다.
 * 파일 형식: 한국어 부분 --- 영어 부분. 각 부분은 '## 제목', 설명 문단, '- ' 항목으로 이뤄집니다.
 * 지금 앱 버전보다 높은 버전(아직 출시 전 노트)은 보여 주지 않습니다.
 */
data class ReleaseNote(
    val version: String,
    val title: String,
    val summary: String,
    val items: List<String>,
    /** 노트에 `<!-- quiet -->`가 있으면 업데이트 후 안내 팝업을 띄우지 않음 (버그 수정만 있는 버전) */
    val quiet: Boolean = false
)

object ReleaseNotes {
    private const val DIR = "release_notes"

    fun load(context: Context, english: Boolean, currentVersion: String): List<ReleaseNote> {
        val names = runCatching { context.assets.list(DIR)?.toList() }.getOrNull().orEmpty()
        return names.mapNotNull { name ->
            val version = versionOf(name) ?: return@mapNotNull null
            if (compare(version, currentVersion) > 0) return@mapNotNull null
            val text = runCatching { context.assets.open("$DIR/$name").bufferedReader().use { it.readText() } }.getOrNull()
                ?: return@mapNotNull null
            parse(version, text, english)
        }.sortedWith { a, b -> compare(b.version, a.version) }
    }

    fun versionOf(fileName: String): String? =
        Regex("""^v(\d+\.\d+\.\d+)\.md$""").find(fileName)?.groupValues?.get(1)

    /** 0.9.40 > 0.9.39 > 0.9.9 처럼 숫자로 비교 (접미사 a·b 등은 무시) */
    fun compare(a: String, b: String): Int {
        fun parts(v: String) = v.split('.').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val x = parts(a); val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    fun parse(version: String, markdown: String, english: Boolean): ReleaseNote {
        val sections = markdown.split(Regex("""(?m)^---\s*$"""))
        val section = (if (english) sections.getOrNull(1) else sections.getOrNull(0))?.takeIf { it.isNotBlank() }
            ?: sections.first()
        var title = "v$version"
        val summary = StringBuilder()
        val items = mutableListOf<String>()
        section.lines().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("<!--") -> Unit
                line.startsWith("## ") -> title = line.removePrefix("## ").trim()
                line.startsWith("- ") -> items += line.removePrefix("- ").trim()
                line.isNotEmpty() && items.isEmpty() -> { if (summary.isNotEmpty()) summary.append(' '); summary.append(line) }
            }
        }
        return ReleaseNote(version, title, summary.toString(), items, quiet = markdown.contains("<!-- quiet -->"))
    }

    /** 지금 버전의 노트 하나 (없으면 null) */
    fun find(context: Context, english: Boolean, currentVersion: String): ReleaseNote? =
        load(context, english, currentVersion).firstOrNull { compare(it.version, currentVersion) == 0 }
}
