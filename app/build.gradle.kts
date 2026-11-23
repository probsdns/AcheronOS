
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val gitHeadFile = rootProject.layout.projectDirectory.file(".git/HEAD")

val gitHead = providers.fileContents(gitHeadFile)
    .asText
    .get()
    .trim()

val currentBranch = gitHead
    .removePrefix("ref: refs/heads/")
    .takeIf { gitHead.startsWith("ref: refs/heads/") }

val branchVersion = currentBranch?.let { branch ->
    Regex("""^v(\d+(?:\.\d+)+)(?:-wip)?$""")
        .matchEntire(branch)
        ?.groupValues
        ?.get(1)
}

val latestStableTag = try {
    providers.exec {
        workingDir = rootProject.projectDir
        commandLine(
            "git",
            "tag",
            "--merged",
            "HEAD",
            "--list",
            "v[0-9]*",
            "--sort=-version:refname"
        )
    }.standardOutput.asText.get()
        .lineSequence()
        .map { it.trim() }
        .firstOrNull {
            Regex("""^v\d+(?:\.\d+)+$""").matches(it)
        }
        ?.removePrefix("v")
} catch (e: Exception) {
    null
}

val acheronosVersion = branchVersion ?: latestStableTag ?: "1.0"

println("AcheronOS Git branch: ${currentBranch ?: "unknown"}")
println("AcheronOS detected version: v$acheronosVersion")

android {
    namespace = "com.example.acheronos"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.acheronos"
        minSdk = 26
        targetSdk = 37

        versionCode = 1
        versionName = acheronosVersion

        buildConfigField(
            "String",
            "ACHERONOS_VERSION",
            "\"v$acheronosVersion\""
        )

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf(
                    "androidx.**",
                    "kotlin.**",
                    "kotlinx.**"
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    testImplementation(libs.junit)

    implementation(
        "androidx.exifinterface:exifinterface:1.3.7"
    )

    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )
    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )
    androidTestImplementation(
        libs.androidx.espresso.core
    )
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )
    debugImplementation(libs.androidx.compose.ui.tooling)
}
