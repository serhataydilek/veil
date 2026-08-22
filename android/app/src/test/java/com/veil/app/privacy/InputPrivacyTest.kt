package com.veil.app.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputPrivacyTest {
    @Test
    fun secretSecurityInputDisablesSuggestionsAndAutofill() {
        val policy = VeilInputPrivacy.SECRET_SECURITY_INPUT.policy()
        assertFalse(policy.allowSuggestions)
        assertFalse(policy.allowAutofill)
        assertTrue(policy.obscureText)
    }

    @Test
    fun privateMessageInputStaysReadableButAvoidsLearning() {
        val policy = VeilInputPrivacy.PRIVATE_MESSAGE_TEXT.policy()
        assertFalse(policy.allowSuggestions)
        assertFalse(policy.allowAutofill)
        assertFalse(policy.obscureText)
    }

    @Test
    fun normalUserTextRemainsUsable() {
        val policy = VeilInputPrivacy.NORMAL_USER_TEXT.policy()
        assertTrue(policy.allowSuggestions)
        assertTrue(policy.allowAutofill)
    }
}
