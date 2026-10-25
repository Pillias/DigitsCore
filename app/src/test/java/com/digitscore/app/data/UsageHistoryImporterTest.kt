package com.digitscore.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageHistoryImporterTest {

    @Test
    fun testImportResultMessage() {
        val emptyResult = UsageHistoryImporter.ImportResult(
            importedDaysCount = 0,
            startDateString = null,
            endDateString = null,
            totalDaysChecked = 7
        )
        assertEquals("불러올 수 있는 지난 기록이 없습니다.", emptyResult.message(isEn = false))
        assertEquals("No past records found to import.", emptyResult.message(isEn = true))

        val successResult = UsageHistoryImporter.ImportResult(
            importedDaysCount = 7,
            startDateString = "2026-10-18",
            endDateString = "2026-10-24",
            totalDaysChecked = 7
        )
        assertEquals("2026-10-18~2026-10-24 기록 7일을 불러왔습니다.", successResult.message(isEn = false))
        assertEquals("Imported 7 days (2026-10-18 to 2026-10-24).", successResult.message(isEn = true))
    }
}
