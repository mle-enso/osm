package de.example.osmnoteswatch

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import org.awaitility.Awaitility.await
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class LocationUiTest : UiTestBase() {

    @Test
    fun locationButtonUsesCurrentEmulatorLocation() {
        val args = InstrumentationRegistry.getArguments()
        val expectedLat = args.getString("expectedLat") ?: "52.5200"
        val expectedLon = args.getString("expectedLon") ?: "13.4050"
        val expected = "$expectedLat,$expectedLon"

        // The emulator's mock GPS provider can take several seconds to deliver a fresh
        // fix to a freshly started app process after `adb emu geo fix`, so a single tap
        // on the locate button can still center on a stale cached location. Keep
        // re-tapping it until the camera actually reports the expected position.
        await().atMost(30, TimeUnit.SECONDS).until {
            onView(withContentDescription("Zum Standort")).perform(click())
            try {
                onView(withContentDescription(containsString(expected))).check(matches(isDisplayed()))
                true
            } catch (_: Throwable) {
                false
            }
        }
    }
}
