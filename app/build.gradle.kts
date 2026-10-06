import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("de.mannodermaus.android-junit")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use(::load)
}
val osmClientId = localProps.getProperty("OSM_CLIENT_ID", "")
val uiTestMode = providers.gradleProperty("uiTestFakeNotes")
    .map { it.toBoolean() }
    .orElse(false)

android {
    namespace = "de.example.osmnoteswatch"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.example.osmnoteswatch"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "OSM_CLIENT_ID", "\"$osmClientId\"")
        buildConfigField("boolean", "UI_TEST_MODE", uiTestMode.get().toString())
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

junitPlatform {
    instrumentationTests.version = "2.0.1"
}

dependencies {
    implementation("org.maplibre.gl:android-sdk-opengl:13.5.2")

    // JUnit 5 / Jupiter for on-device instrumentation tests.
    androidTestImplementation(platform("org.junit:junit-bom:5.14.1"))
    androidTestImplementation("org.junit.jupiter:junit-jupiter-api")

    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("org.awaitility:awaitility:4.2.2")
}

// hamcrest-core/hamcrest-library (pulled in transitively via JUnit 4) duplicate
// classes already bundled into the unified hamcrest 2.x jar that awaitility brings in.
configurations.named("androidTestImplementation") {
    exclude(group = "org.hamcrest", module = "hamcrest-core")
    exclude(group = "org.hamcrest", module = "hamcrest-library")
}
