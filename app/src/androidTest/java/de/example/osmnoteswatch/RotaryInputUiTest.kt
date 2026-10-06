package de.example.osmnoteswatch

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RotaryInputUiTest : UiTestBase() {

    @Test
    fun rotaryInputZoomsMap() {
        await().until { FakeNotesRepository.requestCount.get() > 0 }

        scenario.onActivity { mainActivity ->
            val before = mainActivity.currentZoomForTest()
            val now = SystemClock.uptimeMillis()
            val properties = arrayOf(
                MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_UNKNOWN
                }
            )
            val coordinates = arrayOf(
                MotionEvent.PointerCoords().apply {
                    setAxisValue(MotionEvent.AXIS_SCROLL, 1.0f)
                }
            )
            val event = MotionEvent.obtain(
                now,
                now,
                MotionEvent.ACTION_SCROLL,
                1,
                properties,
                coordinates,
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_ROTARY_ENCODER,
                0
            )

            val handled = mainActivity.dispatchGenericMotionEvent(event)
            event.recycle()
            val after = mainActivity.currentZoomForTest()

            assertTrue(handled, "Rotary event should be handled")
            assertTrue(after > before, "Rotary input should increase zoom ($before -> $after)")
        }
    }
}
