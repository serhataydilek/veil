package com.veil.app.core

/**
 * Mirrors android/app/build.gradle.kts toolchain version parsing.
 * Keep both implementations identical.
 */
internal object FfiToolchainVersion {
    private val versionToken = Regex("""\d+(?:\.\d+)+""")

    fun matchesExact(output: String, toolName: String, expectedVersion: String): Boolean =
        parseVersion(output, toolName) == expectedVersion

    fun parseVersion(output: String, toolName: String): String? {
        val prefix = "$toolName "
        val line = output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith(prefix) }
            ?: return null
        val token = line.removePrefix(prefix).trim().substringBefore(' ').trim()
        if (token.isEmpty() || !versionToken.matches(token)) {
            return null
        }
        return token
    }
}
