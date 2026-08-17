# Android toolchain

**Selected 2026-08-17; all versions are pinned.**

| Component | Version / choice |
|---|---|
| Android Gradle Plugin | 9.2.0 |
| Gradle wrapper | 9.4.1 |
| Kotlin | 2.3.21 |
| Compose BOM | 2026.06.00 |
| compile SDK / target SDK | 36 / 36 |
| min SDK | 26 |
| Java toolchain | JDK 17 minimum; local verification uses JDK 25 |

AGP’s official compatibility page shows AGP 9.2 requires Gradle 9.4.1 and its current configuration example pairs it with Kotlin 2.3.21 ([Android Developers](https://developer.android.com/build/releases/about-agp)). The Compose BOM is used as Android’s recommended compatible Compose dependency set ([Compose BOM](https://developer.android.com/develop/ui/compose/bom)). API 36 is Android 16; the target is deliberately current stable API rather than an Android 17 preview ([Android 16](https://developer.android.com/about/versions/16)).

`minSdk 26` sets the foundation at Android 8.0+, avoiding legacy platform behavior while retaining a broad device base. This is a product-support choice, not a security guarantee; revisit it before release. AGP requires a supported JDK 17-or-newer environment; the local machine supplies JDK 25. The source/target bytecode level remains Java 17 for compatibility.
