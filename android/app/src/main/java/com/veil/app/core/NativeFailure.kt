package com.veil.app.core

internal inline fun <T> catchExpectedNativeFailure(block: () -> T): T? =
    try {
        block()
    } catch (_: Exception) {
        null
    } catch (_: LinkageError) {
        null
    }
