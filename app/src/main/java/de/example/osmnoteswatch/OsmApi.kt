package de.example.osmnoteswatch

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class NoteComment(val user: String?, val action: String, val text: String, val date: String)
data class OsmNote(val id: Long, val lat: Double, val lon: Double, val status: String, val comments: List<NoteComment>)

object OsmApi {
    private const val API = "https://api.openstreetmap.org/api/0.6"

    fun notes(bbox: String): List<OsmNote> {
        val json = request("$API/notes.json?bbox=${enc(bbox)}&closed=7&limit=200")
        val root = JSONObject(json)
        val out = mutableListOf<OsmNote>()
        val features = root.getJSONArray("features")
        for (i in 0 until features.length()) {
            val f = features.getJSONObject(i)
            val coords = f.getJSONObject("geometry").getJSONArray("coordinates")
            val p = f.getJSONObject("properties")
            val cs = p.getJSONArray("comments")
            val comments = mutableListOf<NoteComment>()
            for (j in 0 until cs.length()) {
                val c = cs.getJSONObject(j)
                comments += NoteComment(
                    user = c.optString("user").ifBlank { null },
                    action = c.optString("action"),
                    text = c.optString("text"),
                    date = c.optString("date")
                )
            }
            out += OsmNote(p.getLong("id"), coords.getDouble(1), coords.getDouble(0), p.getString("status"), comments)
        }
        return out
    }

    fun comment(ctx: Context, id: Long, text: String): String =
        authedPost(ctx, "$API/notes/$id/comment.json", mapOf("text" to text))

    fun close(ctx: Context, id: Long, text: String): String =
        authedPost(ctx, "$API/notes/$id/close.json", mapOf("text" to text))

    private fun authedPost(ctx: Context, url: String, form: Map<String, String>): String {
        val token = ctx.getSharedPreferences("auth", Context.MODE_PRIVATE).getString("token", null)
            ?: error("Nicht bei OSM angemeldet")
        return request(url, "POST", form, token)
    }

    fun request(url: String, method: String = "GET", form: Map<String, String>? = null, bearer: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 12000
        conn.readTimeout = 12000
        conn.setRequestProperty("User-Agent", "OSMNotesWatch/0.1 (personal Wear OS app)")
        conn.setRequestProperty("Accept", "application/json")
        if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
        if (form != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val body = form.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream ?: error("HTTP $code"))).use { it.readText() }
        if (code !in 200..299) error("OSM HTTP $code: ${text.take(180)}")
        return text
    }

    fun enc(v: String): String = URLEncoder.encode(v, StandardCharsets.UTF_8.toString())
}
