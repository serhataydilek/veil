package com.veil.app.privacy

/** Reusable privacy classification; it does not claim to control a malicious IME. */
internal enum class VeilInputPrivacy {
    NORMAL_USER_TEXT,
    PRIVATE_MESSAGE_TEXT,
    SECRET_SECURITY_INPUT,
}

internal data class InputPrivacyPolicy(
    val allowSuggestions: Boolean,
    val allowAutofill: Boolean,
    val obscureText: Boolean,
)

internal fun VeilInputPrivacy.policy(): InputPrivacyPolicy = when (this) {
    VeilInputPrivacy.NORMAL_USER_TEXT -> InputPrivacyPolicy(
        allowSuggestions = true,
        allowAutofill = true,
        obscureText = false,
    )
    VeilInputPrivacy.PRIVATE_MESSAGE_TEXT -> InputPrivacyPolicy(
        allowSuggestions = false,
        allowAutofill = false,
        obscureText = false,
    )
    VeilInputPrivacy.SECRET_SECURITY_INPUT -> InputPrivacyPolicy(
        allowSuggestions = false,
        allowAutofill = false,
        obscureText = true,
    )
}
