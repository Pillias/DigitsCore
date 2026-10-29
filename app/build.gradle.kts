import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/** 디버그 빌드 번호를 접미사로 바꿉니다: 0 → "", 1 → a, 26 → z, 27 → aa */
fun debugSuffix(n: Int): String {
    if (n <= 0) return ""
    val sb = StringBuilder()
    var x = n
    while (x > 0) {
        x -= 1
        sb.append('a' + (x % 26))
        x /= 26
    }
    return sb.reverse().toString()
}

android {
    namespace = "com.digitscore.app"
    compileSdk = 36

    val versionPropsFile = rootProject.file("version.properties")
    val versionProps = Properties().apply {
        if (versionPropsFile.exists()) {
            FileInputStream(versionPropsFile).use { load(it) }
        }
    }

    val tagVersion = System.getenv("GITHUB_REF_NAME")
        ?.takeIf { System.getenv("GITHUB_REF_TYPE") == "tag" && it.startsWith("v") }
        ?.removePrefix("v")

    val buildTasks = gradle.startParameter.taskNames.map { it.substringAfterLast(':') }.filter {
        it.contains("assemble", ignoreCase = true) || it.contains("bundle", ignoreCase = true) ||
            it.startsWith("install", ignoreCase = true)
    }
    val isAssemble = buildTasks.isNotEmpty()
    // 변형 이름이 없는 assemble/bundle은 release도 만들므로 릴리스로 봅니다.
    val isReleaseBuild = buildTasks.any {
        it.contains("release", ignoreCase = true) || it.equals("assemble", true) || it.equals("bundle", true)
    }

    // 버전 규칙 (CI 태그 빌드는 태그 버전을 그대로 사용):
    // - 릴리스 빌드: PATCH와 VERSION_CODE를 1 올리고 접미사를 지웁니다. 예) 0.9.33c → 0.9.34
    // - 디버그 빌드: 숫자는 그대로 두고 접미사만 올립니다. 예) 0.9.33 → 0.9.33a → 0.9.33b
    //   (Play versionCode는 릴리스에만 쓰여 0.9.x 상한을 소모하지 않습니다.)
    val alreadyBumpedKey = "DIGITSCORE_VERSION_BUMPED"
    val alreadyBumped = rootProject.extra.has(alreadyBumpedKey)
    val shouldAutoBump = isAssemble && tagVersion == null && !alreadyBumped

    val versionMajor = versionProps.getProperty("MAJOR", "0").toIntOrNull() ?: 0
    val versionMinor = versionProps.getProperty("MINOR", "9").toIntOrNull() ?: 9
    var versionPatch = versionProps.getProperty("PATCH", "0").toIntOrNull() ?: 0
    var versionCodeVal = versionProps.getProperty("VERSION_CODE", "1000").toIntOrNull() ?: 1000
    var debugBuild = versionProps.getProperty("DEBUG_BUILD", "0").toIntOrNull()?.coerceAtLeast(0) ?: 0

    if (shouldAutoBump) {
        rootProject.extra.set(alreadyBumpedKey, true)
        if (isReleaseBuild) {
            versionPatch += 1
            versionCodeVal += 1
            debugBuild = 0
        } else {
            debugBuild += 1
        }
        versionProps.setProperty("PATCH", versionPatch.toString())
        versionProps.setProperty("VERSION_CODE", versionCodeVal.toString())
        versionProps.setProperty("DEBUG_BUILD", debugBuild.toString())
        try {
            FileOutputStream(versionPropsFile).use { fos ->
                versionProps.store(fos, "DigitsCore Version Configuration")
            }
            logger.lifecycle(">> [Auto-Versioning] ${if (isReleaseBuild) "Release" else "Debug"} build -> $versionMajor.$versionMinor.$versionPatch${debugSuffix(debugBuild)} (code: $versionCodeVal)")
        } catch (e: Exception) {
            logger.warn(">> [Auto-Versioning] Failed to auto-bump version.properties: ${e.message}")
        }
    }

    // 0.x 트랙의 versionCode는 MINOR*100+PATCH라 PATCH 100부터 1.0.0(1000)과 겹칩니다.
    if (isAssemble && tagVersion == null && versionMajor == 0 && versionPatch > 99) {
        throw GradleException(
            "버전 $versionMajor.$versionMinor.$versionPatch: 0.x 트랙 PATCH 상한(99)을 넘었습니다. " +
                "versionCode가 1.0.0과 겹치므로 version.properties에서 1.0.0 전환(MAJOR=1, MINOR=0, PATCH=0) 또는 MINOR 변경이 필요합니다."
        )
    }

    val defaultVersion = "$versionMajor.$versionMinor.$versionPatch"
    val numericVersionName = tagVersion ?: defaultVersion
    // 디버그 접미사는 표시용 이름에만 붙고 versionCode 계산에는 쓰지 않습니다.
    val finalVersionName = if (tagVersion != null || isReleaseBuild) numericVersionName
        else numericVersionName + debugSuffix(debugBuild)
    val versionParts = numericVersionName.split(".").mapNotNull { it.toIntOrNull() }
    val calculatedVersionCode = if (versionParts.size >= 3 && versionParts[0] == 0) {
        // 0.9.x 베타 트랙: 1000 안쪽 (900 + patch)
        versionParts[1] * 100 + versionParts[2]
    } else if (versionParts.size >= 3) {
        // 1.0.0+ 정식 출시 트랙: 1000부터 시작
        versionParts[0] * 1000 + versionParts[1] * 100 + versionParts[2]
    } else {
        versionCodeVal
    }

    val buildDate = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

    defaultConfig {
        applicationId = "com.digitscore.app"
        minSdk = 26
        targetSdk = 36
        versionCode = calculatedVersionCode
        versionName = finalVersionName
        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = false
        }
        // 업로드 키 비밀번호는 저장소에 두지 않습니다. CI는 환경변수, 로컬은 git에서 제외된
        // local.properties(digitscore.upload.*)에서 읽습니다.
        val localSigningProps = Properties().apply {
            val file = rootProject.file("local.properties")
            if (file.exists()) FileInputStream(file).use { load(it) }
        }
        fun signingValue(envKey: String, localKey: String): String? =
            System.getenv(envKey)?.takeIf { it.isNotBlank() }
                ?: localSigningProps.getProperty(localKey)?.takeIf { it.isNotBlank() }
        val localKeystore = rootProject.file("keystore/digitscore-upload.jks")
        val uploadStorePath = signingValue("DIGITSCORE_UPLOAD_STORE_FILE", "digitscore.upload.storeFile")
            ?: (if (localKeystore.exists()) localKeystore.absolutePath else null)
        val uploadStorePassword = signingValue("DIGITSCORE_UPLOAD_STORE_PASSWORD", "digitscore.upload.storePassword")
        val uploadKeyAlias = signingValue("DIGITSCORE_UPLOAD_KEY_ALIAS", "digitscore.upload.keyAlias")
        val uploadKeyPassword = signingValue("DIGITSCORE_UPLOAD_KEY_PASSWORD", "digitscore.upload.keyPassword")
        if (!uploadStorePath.isNullOrBlank() &&
            !uploadStorePassword.isNullOrBlank() &&
            !uploadKeyAlias.isNullOrBlank() &&
            !uploadKeyPassword.isNullOrBlank()
        ) {
            create("release") {
                storeFile = file(uploadStorePath)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            // Play 테스트 버전과 나란히 설치되도록 debug 빌드는 별도 패키지로 만듭니다.
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
        // 앱 안 '업데이트 기록': docs/release-notes/*.md를 그대로 담습니다 (한 곳에서만 관리).
        getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/releaseNotesAssets"))
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    applicationVariants.all {
        val variantName = name
        val vName = versionName
        outputs.all {
            (this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl)?.outputFileName =
                "DigitsCore-v${vName}-${variantName}.apk"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)
    ksp(libs.androidx.room.compiler)

    // Glance Widgets
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// docs/release-notes/v*.md → assets/release_notes/ (앱 안 업데이트 기록)
val copyReleaseNotes by tasks.registering(Copy::class) {
    from(rootProject.file("docs/release-notes")) { include("v*.md") }
    into(layout.buildDirectory.dir("generated/releaseNotesAssets/release_notes"))
}
tasks.named("preBuild") { dependsOn(copyReleaseNotes) }
