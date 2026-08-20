package com.veil.app.local

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.veil.app.core.RustCoreBridge
import com.veil.app.security.AndroidLocalProtectionKeyStore

internal class LocalDataViewModel(
    val controller: LocalDataController,
) : ViewModel() {
    override fun onCleared() {
        controller.cancel()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return LocalDataViewModel(
                        LocalDataController(
                            keyStore = AndroidLocalProtectionKeyStore(),
                            storeFactory = SqliteLocalRecordStoreFactory(appContext),
                            cipher = AesGcmLocalRecordCipher(),
                            clock = AndroidRetentionClock(appContext),
                            policyLoader = RustRetentionPolicyLoader(RustCoreBridge()),
                        ),
                    ) as T
                }
            }
        }
    }
}
