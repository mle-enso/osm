package de.example.osmnoteswatch

import android.content.Context
import java.util.concurrent.atomic.AtomicInteger

interface NotesRepository {
    fun notes(bbox: String): List<OsmNote>
    fun comment(context: Context, id: Long, text: String): String
    fun close(context: Context, id: Long, text: String): String
}

object LiveNotesRepository : NotesRepository {
    override fun notes(bbox: String): List<OsmNote> = OsmApi.notes(bbox)
    override fun comment(context: Context, id: Long, text: String): String = OsmApi.comment(context, id, text)
    override fun close(context: Context, id: Long, text: String): String = OsmApi.close(context, id, text)
}

object FakeNotesRepository : NotesRepository {
    val requestCount = AtomicInteger(0)

    fun reset() {
        requestCount.set(0)
    }

    override fun notes(bbox: String): List<OsmNote> {
        requestCount.incrementAndGet()

        val parts = bbox.split(',').mapNotNull(String::toDoubleOrNull)
        val west = parts.getOrElse(0) { 13.395 }
        val south = parts.getOrElse(1) { 52.515 }
        val east = parts.getOrElse(2) { 13.415 }
        val north = parts.getOrElse(3) { 52.525 }
        val centerLat = (south + north) / 2.0
        val centerLon = (west + east) / 2.0

        return listOf(
            OsmNote(
                id = 9001,
                lat = centerLat,
                lon = centerLon,
                status = "open",
                comments = listOf(
                    NoteComment(
                        user = "ui-test",
                        action = "opened",
                        text = "Testhinweis: Hier fehlt ein Straßenschild.",
                        date = "2026-10-06T09:00:00Z"
                    )
                )
            ),
            OsmNote(
                id = 9002,
                lat = centerLat + 0.001,
                lon = centerLon + 0.001,
                status = "closed",
                comments = listOf(
                    NoteComment(
                        user = "ui-test",
                        action = "closed",
                        text = "Testhinweis wurde erledigt.",
                        date = "2026-10-06T09:05:00Z"
                    )
                )
            )
        )
    }

    override fun comment(context: Context, id: Long, text: String): String = "{}"
    override fun close(context: Context, id: Long, text: String): String = "{}"
}

object NotesRepositoryProvider {
    val current: NotesRepository
        get() = if (BuildConfig.UI_TEST_MODE) FakeNotesRepository else LiveNotesRepository
}
