package com.veil.app.privacy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import java.util.UUID

/**
 * The only approved path for an explicit future copy action. Nothing is copied
 * automatically. A short-lived opaque ownership token prevents delayed cleanup
 * from clearing content that another app or the user placed on the clipboard.
 */
internal class ClipboardPrivacy(private val clipboard: ClipboardPort) {
    fun copySensitive(text: CharSequence) {
        val token = UUID.randomUUID().toString()
        clipboard.set(OwnedClipboardClip(token, text))
        clipboard.clearAfter(token, CLEAR_DELAY_MILLIS)
    }

    internal companion object {
        const val CLEAR_DELAY_MILLIS = 60_000L
    }
}

internal data class OwnedClipboardClip(val token: String, val text: CharSequence)

internal interface ClipboardPort {
    fun set(clip: OwnedClipboardClip)
    fun clearAfter(token: String, delayMillis: Long)
}

internal class AndroidClipboardPort(context: Context) : ClipboardPort {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    override fun set(clip: OwnedClipboardClip) {
        val data = ClipData.newPlainText(clip.token, clip.text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.description.setExtras(PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            })
        }
        clipboard.setPrimaryClip(data)
    }

    override fun clearAfter(token: String, delayMillis: Long) {
        handler.postDelayed({
            val current = clipboard.primaryClipDescription
            if (current?.label?.toString() == token) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    // API 26-27 has no clearPrimaryClip; replace only our owned
                    // clip so its sensitive text is no longer available.
                    clipboard.setPrimaryClip(ClipData.newPlainText(token, ""))
                }
            }
        }, delayMillis)
    }
}
