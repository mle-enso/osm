package de.example.osmnoteswatch

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

abstract class UiTestBase {
    protected lateinit var scenario: ActivityScenario<MainActivity>

    @BeforeEach
    fun launchActivityWithLocationPermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName

        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(
                packageName,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(
                packageName,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        FakeNotesRepository.reset()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @AfterEach
    fun closeActivity() {
        if (::scenario.isInitialized) scenario.close()
    }
}
