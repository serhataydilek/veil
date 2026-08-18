package com.veil.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.veil.app.lock.AppPrivacyViewModel
import com.veil.app.privacy.ScreenPrivacy
import com.veil.app.security.AndroidAppAuthenticator
import com.veil.app.ui.VeilApp
import com.veil.app.ui.theme.VeilTheme

class MainActivity : FragmentActivity() {
    private lateinit var authenticator: AndroidAppAuthenticator
    private lateinit var privacyViewModel: AppPrivacyViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScreenPrivacy.apply(this)
        privacyViewModel = ViewModelProvider(
            this,
            AppPrivacyViewModel.factory(this),
        )[AppPrivacyViewModel::class.java]
        authenticator = AndroidAppAuthenticator(this)
        enableEdgeToEdge()
        setContent {
            VeilTheme {
                VeilApp(
                    controller = privacyViewModel.controller,
                    authenticator = authenticator,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::authenticator.isInitialized && ::privacyViewModel.isInitialized) {
            privacyViewModel.controller.refreshAvailability(authenticator)
        }
    }
}
