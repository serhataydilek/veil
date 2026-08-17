package com.veil.app.core

enum class SecurityFeature {
    RENDEZVOUS,
    SECURE_SESSION,
    CONTACT_ID_ISSUANCE,
}

sealed interface SecurityFeatureAvailability {
    data class SecurityReviewRequired(val feature: SecurityFeature) : SecurityFeatureAvailability
}

/**
 * Android-side presentation boundary until the reviewed Rust core is integrated.
 * No feature has an available default in Phase 1A.
 */
object SecurityFeatureGate {
    fun availability(feature: SecurityFeature): SecurityFeatureAvailability =
        SecurityFeatureAvailability.SecurityReviewRequired(feature)
}
