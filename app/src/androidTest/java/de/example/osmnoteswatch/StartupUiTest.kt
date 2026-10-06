package de.example.osmnoteswatch

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.awaitility.Awaitility.await
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StartupUiTest : UiTestBase() {

    @Test
    fun appStartsAndShowsMapLocationButtonAndNotes() {
        onView(withContentDescription(containsString("OSM map"))).check(matches(isDisplayed()))
        onView(withContentDescription("Zum Standort")).check(matches(isDisplayed()))

        await().until {
            try {
                onView(withText(containsString("1 offen"))).check(matches(isDisplayed()))
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    @Test
    fun openNoteCanBeTappedAndRead() {
        await().until {
            try {
                onView(withText(containsString("1 offen"))).check(matches(isDisplayed()))
                true
            } catch (_: Throwable) {
                false
            }
        }
        onView(withContentDescription(containsString("OSM map"))).perform(click())

        // Espresso's root-view picker never settles once the AlertDialog window is
        // added on top of the continuously-rendering MapLibre MapView on this device,
        // so it can't be used to detect the dialog. Read the activity's state directly
        // instead, following the same pattern as MainActivity.currentZoomForTest().
        var shownNote: OsmNote? = null
        await().until {
            scenario.onActivity { shownNote = it.shownNoteForTest() }
            shownNote != null
        }

        assertEquals(9001L, shownNote!!.id)
        assertTrue(shownNote!!.comments.any { it.text.contains("Testhinweis: Hier fehlt ein Straßenschild.") })
    }
}
