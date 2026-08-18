package com.veil.app.lock

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.veil.app.security.protectedStateStore

internal class AppPrivacyViewModel(
    val controller: AppPrivacyController,
) : ViewModel(), DefaultLifecycleObserver {
    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        controller.onProcessForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        controller.onProcessBackground()
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        controller.cancel()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return AppPrivacyViewModel(AppPrivacyController(protectedStateStore(appContext))) as T
                }
            }
        }
    }
}
