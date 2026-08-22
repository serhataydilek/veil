package com.veil.app.local

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

internal fun interface BootCountReader {
    fun read(): BootObservation
}

internal class SettingsGlobalBootCountReader(private val context: Context) : BootCountReader {
    override fun read(): BootObservation = try {
        val count = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        if (count < 0) BootObservation(bootCount = null, reliable = false)
        else BootObservation(bootCount = count, reliable = true)
    } catch (_: Settings.SettingNotFoundException) {
        BootObservation(bootCount = null, reliable = false)
    }
}

/** Single Android time boundary for local retention. Repositories must not call platform clocks directly. */
internal class AndroidRetentionClock(
    private val bootCountReader: BootCountReader,
) : RetentionClock {
    constructor(context: Context) : this(SettingsGlobalBootCountReader(context.applicationContext))

    override fun wallClockMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()

    override fun bootObservation(): BootObservation = bootCountReader.read()
}
