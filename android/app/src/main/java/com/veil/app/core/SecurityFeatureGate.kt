package com.veil.app.core

enum class SecurityFeature {
    RENDEZVOUS,
    SECURE_SESSION,
    CONTACT_ID_ISSUANCE,
}

sealed interface SecurityFeatureAvailability {
    data class SecurityReviewRequired(val feature: SecurityFeature) : SecurityFeatureAvailability
    data class Unavailable(val feature: SecurityFeature) : SecurityFeatureAvailability
    data class NotImplemented(val feature: SecurityFeature) : SecurityFeatureAvailability
}

class SecurityFeatureGate(
    private val bridge: RustCoreBridge,
) {
    fun availability(feature: SecurityFeature): SecurityFeatureAvailability =
        when (feature) {
            SecurityFeature.CONTACT_ID_ISSUANCE ->
                SecurityFeatureAvailability.NotImplemented(feature)
            SecurityFeature.RENDEZVOUS,
            SecurityFeature.SECURE_SESSION,
            -> rustBackedAvailability(feature)
        }

    private fun rustBackedAvailability(feature: SecurityFeature): SecurityFeatureAvailability {
        val snapshot = bridge.load()
        if (snapshot.status != CoreBridgeStatus.AVAILABLE) {
            return SecurityFeatureAvailability.Unavailable(feature)
        }
        val rustStatus = when (feature) {
            SecurityFeature.RENDEZVOUS -> snapshot.rendezvousStatus
            SecurityFeature.SECURE_SESSION -> snapshot.secureSessionStatus
            SecurityFeature.CONTACT_ID_ISSUANCE -> null
        }
        return when (rustStatus) {
            CoreSecurityGateStatus.REVIEW_REQUIRED ->
                SecurityFeatureAvailability.SecurityReviewRequired(feature)
            null -> SecurityFeatureAvailability.Unavailable(feature)
        }
    }
}
