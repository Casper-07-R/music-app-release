package com.koi.backend.player

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryOptimizationHelperTest {

    private class MockContext : ContextWrapper(null) {
        override fun getSystemService(name: String): Any? = null
        override fun getPackageName(): String = "com.koi.ui"
    }

    @Test
    fun testIsIgnoringBatteryOptimizations_nullPowerManager_returnsTrueByDefault() {
        val mockContext = MockContext()
        val result = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(mockContext)
        // Default when powerManager is null is true
        assertTrue(result)
    }
}
