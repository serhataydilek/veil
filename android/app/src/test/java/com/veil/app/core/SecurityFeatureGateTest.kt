package com.veil.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityFeatureGateTest {
    @Test
    fun security_backed_features_default_to_review_required() {
        for (feature in SecurityFeature.entries) {
            assertEquals(
                SecurityFeatureAvailability.SecurityReviewRequired(feature),
                SecurityFeatureGate.availability(feature),
            )
        }
    }
}
