# Android data-protection baseline

Phase 1A sets `android:allowBackup="false"` and supplies both legacy `fullBackupContent` and Android 12+ `dataExtractionRules` that exclude all app data from cloud backup and device transfer. There is no application persistence yet, but the baseline avoids silently making future data portable by default.

Android Auto Backup applies by default to apps targeting/running API 23+; Android documentation specifically warns that, on some Android 12+ manufacturer devices, `allowBackup="false"` might disable cloud backup but not device-to-device transfer. Apps targeting API 31+ use data-extraction rules, whose cloud-backup and device-transfer sections are distinct ([Auto Backup](https://developer.android.com/identity/data/autobackup)). Both configuration paths are therefore present.

This is configuration, not a proof of deletion or universal OEM behavior. Before identity or message persistence, validate backup/restore and transfer behavior on supported Android versions/OEMs, re-check Android’s current rules, and add platform-specific storage controls. Phase 1C adds always-on `FLAG_SECURE` and API 33+ recents protection; clipboard cleanup and keyboard-learning controls remain later.
