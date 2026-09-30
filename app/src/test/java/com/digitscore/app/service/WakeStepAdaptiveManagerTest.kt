package com.digitscore.app.service

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*

class WakeStepAdaptiveManagerTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private val prefMap = mutableMapOf<String, Any>()

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        sharedPreferences = mock(SharedPreferences::class.java)
        editor = mock(SharedPreferences.Editor::class.java)

        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPreferences)
        `when`(sharedPreferences.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenAnswer { invocation ->
            prefMap[invocation.getArgument(0)] = invocation.getArgument(1)
            editor
        }
        `when`(editor.putInt(anyString(), anyInt())).thenAnswer { invocation ->
            prefMap[invocation.getArgument(0)] = invocation.getArgument(1)
            editor
        }
        `when`(sharedPreferences.getString(anyString(), anyString())).thenAnswer { invocation ->
            (prefMap[invocation.getArgument(0)] as? String) ?: invocation.getArgument(1)
        }
        `when`(sharedPreferences.getInt(anyString(), anyInt())).thenAnswer { invocation ->
            (prefMap[invocation.getArgument(0)] as? Int) ?: invocation.getArgument(1)
        }
    }

    @Test
    fun defaultThreshold_returnsFifty() {
        val threshold = WakeStepAdaptiveManager.getThreshold(context)
        assertEquals(50, threshold)
    }

    @Test
    fun recordSample_underThreeDays_keepsDefaultThreshold() {
        WakeStepAdaptiveManager.recordSample(context, 80f)
        WakeStepAdaptiveManager.recordSample(context, 100f)

        val threshold = WakeStepAdaptiveManager.getThreshold(context)
        assertEquals(50, threshold)
        assertEquals(2, WakeStepAdaptiveManager.getSampleCount(context))
    }

    @Test
    fun recordSample_threeDaysOrMore_calibratesToTwoThirdsMedian() {
        // Samples: 60, 90, 120 -> Median: 90 -> 2/3 of 90 = 60
        WakeStepAdaptiveManager.recordSample(context, 60f)
        WakeStepAdaptiveManager.recordSample(context, 90f)
        WakeStepAdaptiveManager.recordSample(context, 120f)

        val threshold = WakeStepAdaptiveManager.getThreshold(context)
        assertEquals(60, threshold)
        assertEquals(3, WakeStepAdaptiveManager.getSampleCount(context))
    }

    @Test
    fun recordSample_lowMovement_clampsToMinimumTwenty() {
        // Samples: 15, 20, 25 -> Median: 20 -> 2/3 of 20 = 13 -> Clamped to min 20
        WakeStepAdaptiveManager.recordSample(context, 15f)
        WakeStepAdaptiveManager.recordSample(context, 20f)
        WakeStepAdaptiveManager.recordSample(context, 25f)

        val threshold = WakeStepAdaptiveManager.getThreshold(context)
        assertEquals(20, threshold)
    }
}
