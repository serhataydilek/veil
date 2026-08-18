package com.veil.app.privacy

import android.os.Build
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import com.veil.app.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidScreenPrivacyTest {
    @Test
    fun mainActivitySetsFlagSecure() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
        }
    }

    @Test
    fun recentsProtectionSetupDoesNotCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activity.setRecentsScreenshotEnabled(false)
                }
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
        }
    }
}
