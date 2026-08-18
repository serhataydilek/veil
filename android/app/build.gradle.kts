import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.lang.ProcessBuilder
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val veilRoot = rootDir.parentFile
val rustDir = File(veilRoot, "rust")
val cargoNdkVersion = providers.gradleProperty("veil.cargoNdkVersion").get()
val rustVersion = providers.gradleProperty("veil.rustVersion").get()
val nativeApiLevel = providers.gradleProperty("veil.nativeApiLevel").get()
val rustAbis = listOf("arm64-v8a", "x86_64")
val uniffiKotlinDir = layout.buildDirectory.dir("generated/source/uniffi/kotlin")
val debugJniDir = layout.buildDirectory.dir("generated/jniLibs/debug")
val releaseJniDir = layout.buildDirectory.dir("generated/jniLibs/release")
val hostRustTargetDir = layout.buildDirectory.dir("rust/host")
val debugRustTargetDir = layout.buildDirectory.dir("rust/android-debug")
val releaseRustTargetDir = layout.buildDirectory.dir("rust/android-release")

android {
    namespace = "com.veil.app"
    compileSdk = 36
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = "com.veil.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += rustAbis
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }
}

androidComponents {
    onVariants { variant ->
        val kotlinOut = uniffiKotlinDir.get().asFile
        kotlinOut.mkdirs()
        variant.sources.java?.addStaticSourceDirectory(kotlinOut.absolutePath)
        val jniOut = when (variant.buildType) {
            "debug" -> debugJniDir.get().asFile
            "release" -> releaseJniDir.get().asFile
            else -> null
        }
        if (jniOut != null) {
            jniOut.mkdirs()
            variant.sources.jniLibs?.addStaticSourceDirectory(jniOut.absolutePath)
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.coroutines.android)
    implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
    testImplementation("net.java.dev.jna:jna:${libs.versions.jna.get()}")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register("ensureRustAndroidFfiToolchain") {
    group = "veil"
    description = "Verify pinned Rust, Android targets, NDK, and cargo-ndk."
    doLast {
        check(rustDir.isDirectory) { "Rust workspace is missing at ${rustDir.invariantSeparatorsPath}" }
        val cargo = findCargo()
        val rustc = findSiblingTool(cargo, "rustc")
        val rustcVersion = runCapture(rustDir, listOf(rustc.absolutePath, "--version"))
        check(rustcVersion.contains(rustVersion)) {
            "Pinned Rust $rustVersion is required, found: ${rustcVersion.trim()}"
        }
        val rustup = findSiblingTool(cargo, "rustup")
        runChecked(
            rustDir,
            listOf(
                rustup.absolutePath,
                "target",
                "add",
                "aarch64-linux-android",
                "x86_64-linux-android",
            ),
        )
        ensureCargoNdk(cargo)
        val ndkHome = ndkHomeDir()
        check(ndkHome.isDirectory) {
            "Pinned NDK ${libs.versions.ndk.get()} is required at ${ndkHome.invariantSeparatorsPath}"
        }
    }
}

tasks.register("buildHostVeilFfi") {
    group = "veil"
    description = "Build the host veil-ffi library used for UniFFI bindgen."
    dependsOn("ensureRustAndroidFfiToolchain")
    inputs.files(
        fileTree(rustDir) {
            include("Cargo.toml", "Cargo.lock", "rust-toolchain.toml")
            include("crates/**/*.rs", "crates/**/*.toml")
            include("tools/**/*.rs", "tools/**/*.toml")
        },
    )
    outputs.dir(hostRustTargetDir)
    doLast {
        val cargo = findCargo()
        val targetDir = hostRustTargetDir.get().asFile
        targetDir.mkdirs()
        runChecked(
            rustDir,
            listOf(cargo.absolutePath, "build", "-p", "veil-ffi", "--locked"),
            mapOf("CARGO_TARGET_DIR" to targetDir.absolutePath),
        )
        check(hostCdylib(targetDir).isFile) {
            "Host veil-ffi library was not produced for UniFFI bindgen."
        }
    }
}

tasks.register("generateUniffiKotlin") {
    group = "veil"
    description = "Generate Kotlin UniFFI bindings into the Gradle build directory."
    dependsOn("buildHostVeilFfi")
    inputs.files(
        fileTree(rustDir) {
            include("Cargo.toml", "Cargo.lock", "crates/veil-ffi/**", "tools/veil-uniffi-bindgen/**")
        },
    )
    outputs.dir(uniffiKotlinDir)
    doLast {
        val cargo = findCargo()
        val outDir = uniffiKotlinDir.get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()
        val library = hostCdylib(hostRustTargetDir.get().asFile)
        runChecked(
            rustDir,
            listOf(
                cargo.absolutePath,
                "run",
                "-p",
                "veil-uniffi-bindgen",
                "--locked",
                "--",
                "generate",
                "--library",
                library.absolutePath,
                "--language",
                "kotlin",
                "--no-format",
                "--out-dir",
                outDir.absolutePath,
            ),
            mapOf("CARGO_TARGET_DIR" to hostRustTargetDir.get().asFile.absolutePath),
        )
        val generated = File(outDir, "uniffi/veil_ffi/veil_ffi.kt")
        check(generated.isFile) { "UniFFI Kotlin bindings were not generated." }
    }
}

fun registerRustJniTask(variant: String, release: Boolean, outputDir: Provider<Directory>, targetDir: Provider<Directory>) {
    val cap = variant.replaceFirstChar { it.uppercase() }
    tasks.register("buildRustJni$cap") {
        group = "veil"
        description = "Build Android $variant libveil_ffi.so via cargo-ndk $cargoNdkVersion."
        dependsOn("ensureRustAndroidFfiToolchain")
        inputs.files(
            fileTree(rustDir) {
                include("Cargo.toml", "Cargo.lock", "rust-toolchain.toml")
                include("crates/**/*.rs", "crates/**/*.toml")
            },
        )
        outputs.dir(outputDir)
        doLast {
            val cargo = findCargo()
            val jniOut = outputDir.get().asFile
            jniOut.deleteRecursively()
            jniOut.mkdirs()
            val cargoTarget = targetDir.get().asFile
            cargoTarget.mkdirs()
            val command = mutableListOf(
                cargo.absolutePath,
                "ndk",
            )
            rustAbis.forEach { abi ->
                command += listOf("-t", abi)
            }
            command += listOf(
                "--platform",
                nativeApiLevel,
                "-o",
                jniOut.absolutePath,
                "build",
                "-p",
                "veil-ffi",
                "--locked",
            )
            if (release) {
                command += "--release"
            }
            runChecked(
                rustDir,
                command,
                mapOf(
                    "CARGO_TARGET_DIR" to cargoTarget.absolutePath,
                    "ANDROID_NDK_HOME" to ndkHomeDir().absolutePath,
                    "ANDROID_NDK_ROOT" to ndkHomeDir().absolutePath,
                ),
            )
            rustAbis.forEach { abi ->
                val so = File(jniOut, "$abi/libveil_ffi.so")
                check(so.isFile) { "Missing $variant native library for $abi" }
            }
        }
    }
}

registerRustJniTask("debug", release = false, debugJniDir, debugRustTargetDir)
registerRustJniTask("release", release = true, releaseJniDir, releaseRustTargetDir)

tasks.withType<KotlinCompile>().configureEach {
    dependsOn("generateUniffiKotlin")
}

tasks.configureEach {
    when (name) {
        "mergeDebugNativeLibs", "mergeDebugJniLibFolders" -> dependsOn("buildRustJniDebug")
        "mergeReleaseNativeLibs", "mergeReleaseJniLibFolders" -> dependsOn("buildRustJniRelease")
        "assembleDebug" -> finalizedBy("verifyDebugApkNativeLibs")
        "assembleRelease" -> finalizedBy("verifyReleaseApkNativeLibs")
    }
}

fun registerApkNativeVerification(variant: String, assembleTask: String, apkGlob: String) {
    val cap = variant.replaceFirstChar { it.uppercase() }
    tasks.register("verify${cap}ApkNativeLibs") {
        group = "veil"
        description = "Inspect the $variant APK for packaged veil-ffi native libraries."
        dependsOn(assembleTask)
        doLast {
            val apk = fileTree(layout.buildDirectory.dir("outputs/apk/$variant")).matching {
                include(apkGlob)
            }.files.singleOrNull()
            check(apk != null && apk.isFile) { "Could not find $variant APK to inspect." }
            ZipFile(apk).use { zip ->
                val names = zip.entries().toList().map { it.name }
                rustAbis.forEach { abi ->
                    check(names.contains("lib/$abi/libveil_ffi.so")) {
                        "APK is missing lib/$abi/libveil_ffi.so"
                    }
                }
                val forbidden = names.filter { name ->
                    name.endsWith(".pdb") ||
                        name.endsWith(".rs") ||
                        name.endsWith("Cargo.toml") ||
                        name.endsWith("Cargo.lock") ||
                        name.contains(".cargo/registry") ||
                        name.contains("rust/crates/")
                }
                check(forbidden.isEmpty()) {
                    "APK contains unexpected native/source artifacts: $forbidden"
                }
            }
        }
    }
}

registerApkNativeVerification("debug", "assembleDebug", "**/app-debug.apk")
registerApkNativeVerification("release", "assembleRelease", "**/app-release*.apk")

fun findCargo(): File {
    val fromPath = findOnPath("cargo")
    if (fromPath != null) return fromPath
    val cargoHome = System.getenv("CARGO_HOME")?.let(::File)
        ?: File(System.getProperty("user.home"), ".cargo")
    val candidate = File(cargoHome, "bin/${if (isWindows()) "cargo.exe" else "cargo"}")
    check(candidate.isFile) { "cargo was not found. Install Rust $rustVersion via rustup." }
    return candidate
}

fun findSiblingTool(cargo: File, name: String): File {
    val sibling = File(cargo.parentFile, if (isWindows()) "$name.exe" else name)
    if (sibling.isFile) return sibling
    val fromPath = findOnPath(name)
    check(fromPath != null) { "$name was not found next to cargo or on PATH." }
    return fromPath
}

fun findOnPath(name: String): File? {
    val executable = if (isWindows()) "$name.exe" else name
    return System.getenv("PATH")
        ?.split(File.pathSeparator)
        ?.map { File(it, executable) }
        ?.firstOrNull { it.isFile }
}

fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

fun ndkHomeDir(): File {
    val sdk = System.getenv("ANDROID_SDK_ROOT")
        ?: System.getenv("ANDROID_HOME")
        ?: error("ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK")
    return File(sdk, "ndk/${libs.versions.ndk.get()}")
}

fun hostCdylib(targetDir: File): File {
    val debugDir = File(targetDir, "debug")
    val candidates = listOf(
        File(debugDir, "veil_ffi.dll"),
        File(debugDir, "libveil_ffi.so"),
        File(debugDir, "libveil_ffi.dylib"),
    )
    return candidates.firstOrNull { it.isFile }
        ?: error("Host veil-ffi cdylib was not found under ${debugDir.invariantSeparatorsPath}")
}

fun ensureCargoNdk(cargo: File) {
    val versionOutput = runCapture(rustDir, listOf(cargo.absolutePath, "ndk", "--version"))
    if (versionOutput.contains(cargoNdkVersion)) {
        return
    }
    runChecked(
        rustDir,
        listOf(
            cargo.absolutePath,
            "install",
            "cargo-ndk",
            "--version",
            cargoNdkVersion,
            "--locked",
        ),
    )
    val installed = runCapture(rustDir, listOf(cargo.absolutePath, "ndk", "--version"))
    check(installed.contains(cargoNdkVersion)) {
        "cargo-ndk $cargoNdkVersion is required, found: ${installed.trim()}"
    }
}

fun runChecked(workingDir: File, command: List<String>, env: Map<String, String> = emptyMap()) {
    val result = runProcess(workingDir, command, env)
    check(result.exitCode == 0) {
        "Command failed (${result.exitCode}): ${command.joinToString(" ")}\n${result.output}"
    }
}

fun runCapture(workingDir: File, command: List<String>): String {
    return runProcess(workingDir, command, emptyMap(), ignoreExitValue = true).output
}

fun runProcess(
    workingDir: File,
    command: List<String>,
    env: Map<String, String>,
    ignoreExitValue: Boolean = false,
): ProcessResult {
    val builder = ProcessBuilder(command)
    builder.directory(workingDir)
    builder.redirectErrorStream(true)
    builder.environment().putAll(env)
    val process = builder.start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    if (!ignoreExitValue && exitCode != 0) {
        check(false) {
            "Command failed ($exitCode): ${command.joinToString(" ")}\n$output"
        }
    }
    return ProcessResult(exitCode, output)
}

class ProcessResult(val exitCode: Int, val output: String)
