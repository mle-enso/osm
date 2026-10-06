package de.example.osmnoteswatch

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import org.awaitility.Awaitility.await
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapInteractionUiTest : UiTestBase() {

    @Test
    fun movingMapReloadsNotesForNewBoundingBox() {
        await().until { FakeNotesRepository.requestCount.get() >= 1 }
        val before = FakeNotesRepository.requestCount.get()

        onView(withContentDescription(containsString("OSM map"))).perform(swipeLeft())

        await().until { FakeNotesRepository.requestCount.get() > before }
        assertTrue(FakeNotesRepository.requestCount.get() > before)
    }
}
