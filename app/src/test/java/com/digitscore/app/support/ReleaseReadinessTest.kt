package com.digitscore.app.support

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseReadinessTest {
    @Test
    fun checklistContainsEveryRequiredAndProductItem() {
        assertEquals((1..10).map(Int::toString), ReleaseReadiness.required.map { it.number })
        assertEquals(6, ReleaseReadiness.product.size)
        assertEquals(8, ReleaseReadiness.count(ReadinessStatus.COMPLETE))
        assertEquals(3, ReleaseReadiness.count(ReadinessStatus.READY_TO_VALIDATE))
        assertEquals(2, ReleaseReadiness.count(ReadinessStatus.OWNER_ACTION))
        assertEquals(3, ReleaseReadiness.count(ReadinessStatus.EXTERNAL_VALIDATION))
    }
}
