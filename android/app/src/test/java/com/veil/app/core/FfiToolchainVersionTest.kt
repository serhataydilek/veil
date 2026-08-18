package com.veil.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfiToolchainVersionTest {
    @Test
    fun cargoNdk_4_1_2_isAccepted() {
        assertTrue(FfiToolchainVersion.matchesExact("cargo-ndk 4.1.2", "cargo-ndk", "4.1.2"))
        assertTrue(FfiToolchainVersion.matchesExact("cargo-ndk 4.1.2\n", "cargo-ndk", "4.1.2"))
    }

    @Test
    fun cargoNdk_4_1_20_isRejected() {
        assertFalse(FfiToolchainVersion.matchesExact("cargo-ndk 4.1.20", "cargo-ndk", "4.1.2"))
    }

    @Test
    fun cargoNdk_14_1_2_isRejected() {
        assertFalse(FfiToolchainVersion.matchesExact("cargo-ndk 14.1.2", "cargo-ndk", "4.1.2"))
    }

    @Test
    fun cargoNdk_4_1_1_isRejected() {
        assertFalse(FfiToolchainVersion.matchesExact("cargo-ndk 4.1.1", "cargo-ndk", "4.1.2"))
    }

    @Test
    fun cargoNdk_malformed_isRejected() {
        assertFalse(FfiToolchainVersion.matchesExact("", "cargo-ndk", "4.1.2"))
        assertFalse(FfiToolchainVersion.matchesExact("4.1.2", "cargo-ndk", "4.1.2"))
        assertFalse(FfiToolchainVersion.matchesExact("cargo-ndk", "cargo-ndk", "4.1.2"))
        assertFalse(FfiToolchainVersion.matchesExact("cargo-ndk not-a-version", "cargo-ndk", "4.1.2"))
        assertFalse(FfiToolchainVersion.matchesExact("ndk 4.1.2", "cargo-ndk", "4.1.2"))
    }

    @Test
    fun rustc_1_88_0_isAccepted() {
        assertTrue(
            FfiToolchainVersion.matchesExact(
                "rustc 1.88.0 (6b00bc388 2025-06-23)",
                "rustc",
                "1.88.0",
            ),
        )
    }

    @Test
    fun rustc_otherPatchOrMinor_isRejected() {
        assertFalse(FfiToolchainVersion.matchesExact("rustc 1.88.1 (aaaaaaaa 2025-07-01)", "rustc", "1.88.0"))
        assertFalse(FfiToolchainVersion.matchesExact("rustc 1.89.0 (aaaaaaaa 2025-07-01)", "rustc", "1.88.0"))
        assertFalse(FfiToolchainVersion.matchesExact("rustc 1.88.00 (aaaaaaaa 2025-06-23)", "rustc", "1.88.0"))
    }

    @Test
    fun rustc_malformed_isRejected() {
        assertFalse(FfiToolchainVersion.matchesExact("rustc", "rustc", "1.88.0"))
        assertFalse(FfiToolchainVersion.matchesExact("1.88.0", "rustc", "1.88.0"))
        assertFalse(FfiToolchainVersion.matchesExact("not rustc output", "rustc", "1.88.0"))
    }
}
