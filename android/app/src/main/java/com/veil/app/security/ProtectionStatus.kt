package com.veil.app.security

/**
 * State of the Android-only local at-rest protection substrate.
 * This is deliberately not Veil protocol identity state.
 */
enum class ProtectionStatus {
    CHECKING,
    NOT_PROVISIONED,
    PROVISIONING,
    READY,
    KEY_UNAVAILABLE,
    CORRUPT_OR_UNREADABLE,
    MIGRATION_FAILED,
    PURGING,
    PURGED,
    ERROR,
}

data class PurgeResult(
    val complete: Boolean,
    val status: ProtectionStatus,
)

internal data class ProtectedLoadResult(
    val status: ProtectionStatus,
    val payload: ProtectedLocalPayload?,
)
