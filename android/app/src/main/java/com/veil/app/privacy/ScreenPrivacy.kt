package com.veil.app.privacy

import android.app.Activity
import android.os.Build
import android.view.WindowManager

/** Window-level capture limits. These are not a universal screenshot guarantee. */
internal object ScreenPrivacy {
    fun apply(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(false)
        }
    }
}
