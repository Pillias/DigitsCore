package com.digitscore.app.service

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class WakeStepAdaptiveManagerTest {

    private lateinit var fakePrefs: SharedPreferences
    private val prefMap = mutableMapOf<String, Any>()

    @Before
    fun setUp() {
        prefMap.clear()

        val editorProxy = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "putString" -> {
                    prefMap[args[0] as String] = args[1] as String
                    proxy
                }
                "putInt" -> {
                    prefMap[args[0] as String] = args[1] as Int
                    proxy
                }
                "apply", "commit" -> null
                else -> proxy
            }
        } as SharedPreferences.Editor

        fakePrefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "edit" -> editorProxy
                "getString" -> (prefMap[args[0] as String] as? String) ?: (args[1] as? String)
                "getInt" -> (prefMap[args[0] as String] as? Int) ?: (args[1] as Int)
                else -> null
            }
        } as SharedPreferences
    }

    @Test
    fun defaultThreshold_returnsFifty() {
        val threshold = WakeStepAdaptiveManager.getThreshold(fakePrefs)
        assertEquals(50, threshold)
    }

    @Test
    fun recordSample_underThreeDays_keepsDefaultThreshold() {
        WakeStepAdaptiveManager.recordSample(fakePrefs, 80f)
        WakeStepAdaptiveManager.recordSample(fakePrefs, 100f)

        val threshold = WakeStepAdaptiveManager.getThreshold(fakePrefs)
        assertEquals(50, threshold)
        assertEquals(2, WakeStepAdaptiveManager.getSampleCount(fakePrefs))
    }

    @Test
    fun recordSample_threeDaysOrMore_calibratesToTwoThirdsMedian() {
        // Samples: 60, 90, 120 -> Median: 90 -> 2/3 of 90 = 60
        WakeStepAdaptiveManager.recordSample(fakePrefs, 60f)
        WakeStepAdaptiveManager.recordSample(fakePrefs, 90f)
        WakeStepAdaptiveManager.recordSample(fakePrefs, 120f)

        val threshold = WakeStepAdaptiveManager.getThreshold(fakePrefs)
        assertEquals(60, threshold)
        assertEquals(3, WakeStepAdaptiveManager.getSampleCount(fakePrefs))
    }

    @Test
    fun recordSample_lowMovement_clampsToMinimumTwenty() {
        // Samples: 15, 20, 25 -> Median: 20 -> 2/3 of 20 = 13 -> Clamped to min 20
        WakeStepAdaptiveManager.recordSample(fakePrefs, 15f)
        WakeStepAdaptiveManager.recordSample(fakePrefs, 20f)
        WakeStepAdaptiveManager.recordSample(fakePrefs, 25f)

        val threshold = WakeStepAdaptiveManager.getThreshold(fakePrefs)
        assertEquals(20, threshold)
    }
}
