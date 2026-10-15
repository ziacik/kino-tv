import org.gradle.api.tasks.compile.JavaCompile
import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val torrServerAbi = providers.gradleProperty("torrserverAbi").orElse("arm64-v8a")
val tmdbApiKey = providers.gradleProperty("tmdbApiKey")
    .orElse(providers.environmentVariable("TMDB_API_KEY"))
    .orElse("")
val openSubtitlesApiKey = providers.gradleProperty("opensubtitlesApiKey")
    .orElse(providers.environmentVariable("OPENSUBTITLES_API_KEY"))
    .orElse("")
val releaseKeystoreFile = System.getenv("KINO_KEYSTORE_FILE")
val releaseKeystorePassword = System.getenv("KINO_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("KINO_KEY_ALIAS")
val releaseKeyPassword = System.getenv("KINO_KEY_PASSWORD")
val torrServerAssets = mapOf(
    "arm64-v8a" to Pair(
        "TorrServer-android-arm64",
        "23cea145c38e948f1a967c7fdbcb9c71506cd21a2fe7b3723903e233a323465b",
    ),
    "armeabi-v7a" to Pair(
        "TorrServer-android-arm7",
        "9bab078a0976b86ff392c9eee756194643f4e939ee2c9504dfd4ab7094ef9490",
    ),
)
val generatedTorrServerJniLibsDir = layout.buildDirectory
    .dir("generated/torrserver/jniLibs")
    .get()
    .asFile

val prepareTorrServerBinary = tasks.register("prepareTorrServerBinary") {
    notCompatibleWithConfigurationCache("Downloads and verifies the pinned TorrServer release asset")
    inputs.property("torrserverAbi", torrServerAbi)
    outputs.dir(generatedTorrServerJniLibsDir)

    doLast {
        val abi = torrServerAbi.get()
        val asset = torrServerAssets[abi]
            ?: throw GradleException("Unsupported TorrServer ABI: $abi")
        val assetName = asset.first
        val expectedSha256 = asset.second

        generatedTorrServerJniLibsDir.listFiles()
            ?.filter { it.name != abi }
            ?.forEach { staleAbiDir ->
                if (!staleAbiDir.deleteRecursively()) {
                    throw GradleException("Could not remove stale TorrServer ABI directory: $staleAbiDir")
                }
            }

        val outputDir = generatedTorrServerJniLibsDir.resolve(abi)
        val outputFile = outputDir.resolve("libtorrserver.so")

        fun sha256(file: java.io.File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }

        if (outputFile.isFile && sha256(outputFile) == expectedSha256) {
            logger.lifecycle("Using cached TorrServer $abi binary")
            return@doLast
        }

        outputDir.mkdirs()
        val temporaryFile = outputDir.resolve("libtorrserver.so.tmp")
        temporaryFile.delete()
        outputFile.delete()

        val url = URI(
            "https://github.com/YouROK/TorrServer/releases/download/MatriX.143/$assetName",
        ).toURL()
        logger.lifecycle("Downloading TorrServer $assetName")
        url.openConnection().apply {
            connectTimeout = 30_000
            readTimeout = 120_000
        }.getInputStream().buffered().use { input ->
            temporaryFile.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        }

        val actualSha256 = sha256(temporaryFile)
        if (actualSha256 != expectedSha256) {
            temporaryFile.delete()
            throw GradleException(
                "TorrServer SHA-256 mismatch for $assetName: expected $expectedSha256, got $actualSha256",
            )
        }

        if (!temporaryFile.renameTo(outputFile)) {
            temporaryFile.copyTo(outputFile, overwrite = true)
            temporaryFile.delete()
        }
        logger.lifecycle("Prepared TorrServer $abi at ${outputFile.absolutePath}")
    }
}

android {
    namespace = "sk.ziacik.androidstreamplayer"
    compileSdk = 37

    defaultConfig {
        applicationId = "sk.ziacik.androidstreamplayer"
        minSdk = 26
        targetSdk = 37
        // Increment both values together for a public release; changing this file publishes it.
        versionCode = 2
        versionName = "0.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "TMDB_API_KEY", "\"${tmdbApiKey.get()}\"")
        buildConfigField("String", "OPENSUBTITLES_API_KEY", "\"${openSubtitlesApiKey.get()}\"")
    }

    if (releaseKeystoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystoreFile)
                storePassword = requireNotNull(releaseKeystorePassword) {
                    "KINO_KEYSTORE_PASSWORD is required when KINO_KEYSTORE_FILE is set"
                }
                keyAlias = requireNotNull(releaseKeyAlias) {
                    "KINO_KEY_ALIAS is required when KINO_KEYSTORE_FILE is set"
                }
                keyPassword = requireNotNull(releaseKeyPassword) {
                    "KINO_KEY_PASSWORD is required when KINO_KEYSTORE_FILE is set"
                }
            }
        }

        buildTypes.getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    sourceSets {
        getByName("main").jniLibs.directories.add(generatedTorrServerJniLibsDir.absolutePath)
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += setOf("**/libtorrserver.so")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        disable += setOf("AndroidGradlePluginVersion", "NewerVersionAvailable")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

val verifyTmdbApiKey = tasks.register("verifyTmdbApiKey") {
    doLast {
        require(tmdbApiKey.get().isNotBlank()) {
            "TMDB_API_KEY/tmdbApiKey is required for release builds"
        }
    }
}

val verifyOpenSubtitlesApiKey = tasks.register("verifyOpenSubtitlesApiKey") {
    doLast {
        require(openSubtitlesApiKey.get().isNotBlank()) {
            "OPENSUBTITLES_API_KEY/opensubtitlesApiKey is required for release builds"
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyTmdbApiKey, verifyOpenSubtitlesApiKey)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(prepareTorrServerBinary)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json.jvm)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
