package de.example.osmnoteswatch

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.RectF
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

class MainActivity : Activity() {
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var lastLocation: Location? = null
    private lateinit var status: TextView
    private val notesById = mutableMapOf<Long, OsmNote>()
    private var fitNotesOnNextLoad = true
    private var loadGeneration = 0
    private var lastRequestedBbox: String? = null
    private lateinit var locationManager: LocationManager
    private var locationUpdatesStarted = false
    private var shownNoteForTest: OsmNote? = null

    private val locationListener = LocationListener { loc ->
        lastLocation = loc
        Log.d(
            "OSMNotes",
            "Location update: lat=${loc.latitude}, lon=${loc.longitude}, provider=${loc.provider}"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        val root = FrameLayout(this)
        mapView = MapView(this).apply {
            contentDescription = "OSM map"
        }
        root.addView(mapView, FrameLayout.LayoutParams(-1, -1))

        status = TextView(this).apply {
            text = "Standort…"
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            setPadding(10, 6, 10, 6)
            textSize = 11f
            contentDescription = "Notes status"
        }
        root.addView(status, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = 10
        })

        val attribution = TextView(this).apply {
            text = "© OpenStreetMap"
            setTextColor(Color.WHITE)
            setBackgroundColor(0x88000000.toInt())
            setPadding(6, 3, 6, 3)
            textSize = 8f
        }
        root.addView(attribution, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply {
            setMargins(12, 0, 0, 10)
        })

        val locate = Button(this).apply {
            text = "⌾"
            contentDescription = "Zum Standort"
            textSize = 20f
            background = getDrawable(R.drawable.round_button)
            setTextColor(Color.WHITE)
        }
        // On a round Wear OS display the bottom-right corner is outside the visible circle.
        // Keep the control at the bottom centre so the whole touch target remains visible.
        root.addView(locate, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(18)
        })
        locate.setOnClickListener {
            fitNotesOnNextLoad = false
            centerOnCurrentLocation()
        }
        setContentView(root)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { m ->
            map = m
            val styleBuilder = if (BuildConfig.UI_TEST_MODE) {
                Style.Builder().fromJson(UI_TEST_STYLE)
            } else {
                Style.Builder().fromUri(OPENFREEMAP_STYLE)
            }
            m.setStyle(styleBuilder) { style ->
                installNotesLayers(style)
                installNoteTapHandler(m)
                installCameraReloadHandler(m)
                locateAndLoad(true)
            }
        }
    }


    private fun installNotesLayers(style: Style) {
        style.addSource(GeoJsonSource(OPEN_SOURCE, FeatureCollection.fromFeatures(arrayOf<Feature>())))
        style.addSource(GeoJsonSource(CLOSED_SOURCE, FeatureCollection.fromFeatures(arrayOf<Feature>())))

        style.addLayer(
            CircleLayer(OPEN_LAYER, OPEN_SOURCE).withProperties(
                circleColor(Color.rgb(220, 50, 47)),
                circleRadius(9f),
                circleStrokeColor(Color.WHITE),
                circleStrokeWidth(2.5f)
            )
        )
        style.addLayer(
            CircleLayer(CLOSED_LAYER, CLOSED_SOURCE).withProperties(
                circleColor(Color.rgb(46, 160, 67)),
                circleRadius(9f),
                circleStrokeColor(Color.WHITE),
                circleStrokeWidth(2.5f)
            )
        )
    }

    private fun installNoteTapHandler(m: MapLibreMap) {
        m.addOnMapClickListener { latLng ->
            val screen = m.projection.toScreenLocation(latLng)
            val hitBox = RectF(screen.x - 22f, screen.y - 22f, screen.x + 22f, screen.y + 22f)
            val features = m.queryRenderedFeatures(hitBox, OPEN_LAYER, CLOSED_LAYER)
            val id = features.firstOrNull()?.getNumberProperty("note_id")?.toLong()
            val note = id?.let(notesById::get)
            if (note != null) {
                showNote(note)
                true
            } else false
        }
    }


    private fun installCameraReloadHandler(m: MapLibreMap) {
        m.addOnCameraIdleListener {
            loadNotesForVisibleMap()
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
        ) {
            val currentMap = map ?: return super.onGenericMotionEvent(event)
            val scroll = event.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (scroll == 0f) return true

            val zoomDelta = (scroll * 0.5).coerceIn(-1.5, 1.5)
            val newZoom = (currentMap.cameraPosition.zoom + zoomDelta).coerceIn(2.0, 19.0)

            Log.d("OSMNotes", "Rotary zoom: scroll=$scroll, zoom=${currentMap.cameraPosition.zoom} -> $newZoom")
            currentMap.moveCamera(CameraUpdateFactory.zoomTo(newZoom))
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun locateAndLoad(move: Boolean) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                7
            )
            return
        }

        startLocationUpdates()

        val loc = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }

        if (loc != null) {
            useLocation(loc, move)
        } else {
            status.text = "GPS wird gesucht…"
            runCatching {
                locationManager.requestSingleUpdate(
                    LocationManager.GPS_PROVIDER,
                    { l -> useLocation(l, true) },
                    mainLooper
                )
            }.onFailure { status.text = "Kein Standort verfügbar" }
        }
    }

    private fun useLocation(loc: Location, move: Boolean) {
        lastLocation = loc
        Log.d(
            "OSMNotes",
            "Location update: lat=${loc.latitude}, lon=${loc.longitude}, provider=${loc.provider}"
        )

        if (move) {
            moveCameraToLocation(loc)
        } else {
            loadNotesForVisibleMap()
        }
    }

    private fun centerOnCurrentLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locateAndLoad(true)
            return
        }

        // Normally this is continuously refreshed by locationListener. Re-read the provider
        // caches as well so an emulator "Set location" change is picked up immediately.
        val freshest = if (BuildConfig.UI_TEST_MODE) {
            // `adb emu geo fix` updates the GPS provider. Prefer it in CI so a possibly
            // newer cached network-provider location cannot mask the test position.
            runCatching { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
                ?: lastLocation
        } else {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { provider ->
                    runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
                }
                .plus(listOfNotNull(lastLocation))
                .maxByOrNull { it.time }
        }

        if (freshest != null) {
            lastLocation = freshest
            Log.d(
                "OSMNotes",
                "Center on location: lat=${freshest.latitude}, lon=${freshest.longitude}, provider=${freshest.provider}"
            )
            moveCameraToLocation(freshest)
        } else {
            status.text = "GPS wird gesucht…"
            locateAndLoad(true)
        }
    }

    private fun moveCameraToLocation(loc: Location) {
        if (BuildConfig.UI_TEST_MODE) {
            mapView.contentDescription = String.format(Locale.US, "OSM map centered at %.4f,%.4f", loc.latitude, loc.longitude)
        }
        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(loc.latitude, loc.longitude),
                15.0
            ),
            350
        )
        // Notes are reloaded automatically by OnCameraIdleListener.
    }

    private fun startLocationUpdates() {
        if (locationUpdatesStarted) return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        var registered = false

        if (runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
            runCatching {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    locationListener,
                    mainLooper
                )
                registered = true
            }.onFailure { Log.w("OSMNotes", "GPS location updates unavailable", it) }
        }

        if (runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
            runCatching {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    0f,
                    locationListener,
                    mainLooper
                )
                registered = true
            }.onFailure { Log.w("OSMNotes", "Network location updates unavailable", it) }
        }

        locationUpdatesStarted = registered
    }

    private fun stopLocationUpdates() {
        if (!locationUpdatesStarted) return
        runCatching { locationManager.removeUpdates(locationListener) }
        locationUpdatesStarted = false
    }

    private fun loadNotesForVisibleMap(force: Boolean = false) {
        val m = map ?: return
        val bounds = m.projection.visibleRegion.latLngBounds

        val bbox = listOf(
            bounds.longitudeWest,
            bounds.latitudeSouth,
            bounds.longitudeEast,
            bounds.latitudeNorth
        ).joinToString(",")

        Log.d("OSMNotes", "bbox: $bbox")

        // Camera-idle can occasionally fire more than once for the exact same view.
        if (!force && bbox == lastRequestedBbox) return
        lastRequestedBbox = bbox

        status.text = "Hinweise laden…"
        val generation = ++loadGeneration

        Thread {
            try {
                val notes = NotesRepositoryProvider.current.notes(bbox)
                runOnUiThread {
                    // Ignore a slow response belonging to an older map position.
                    if (generation == loadGeneration) {
                        renderNotes(notes)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (generation == loadGeneration) {
                        status.text = "Fehler: ${e.message?.take(45)}"
                    }
                }
            }
        }.start()
    }

    private fun renderNotes(notes: List<OsmNote>) {
        notesById.clear()
        notes.forEach { notesById[it.id] = it }

        val openFeatures = notes.filter { it.status == "open" }.map(::noteFeature)
        val closedFeatures = notes.filter { it.status != "open" }.map(::noteFeature)

        map?.style?.getSourceAs<GeoJsonSource>(OPEN_SOURCE)
            ?.setGeoJson(FeatureCollection.fromFeatures(openFeatures))
        map?.style?.getSourceAs<GeoJsonSource>(CLOSED_SOURCE)
            ?.setGeoJson(FeatureCollection.fromFeatures(closedFeatures))

        val openCount = openFeatures.size
        val closedCount = closedFeatures.size
        status.text = when {
            closedCount > 0 -> "$openCount offen · $closedCount geschlossen"
            else -> "$openCount offene Hinweise"
        }

        if (fitNotesOnNextLoad) {
            fitNotesOnNextLoad = false
            if (!BuildConfig.UI_TEST_MODE) {
                fitMapToLocationAndNotes(notes)
            }
        }
    }

    private fun fitMapToLocationAndNotes(notes: List<OsmNote>) {
        val m = map ?: return
        val location = lastLocation ?: return

        if (notes.isEmpty()) return

        val boundsBuilder = LatLngBounds.Builder()
            .include(LatLng(location.latitude, location.longitude))
        notes.forEach { boundsBuilder.include(LatLng(it.lat, it.lon)) }

        val bounds = runCatching { boundsBuilder.build() }.getOrNull() ?: return
        mapView.post {
            // Extra padding is important on a round display: the rectangular map view has
            // corners that are physically outside the visible watch face.
            val padding = dp(42)
            runCatching {
                m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding), 450)
            }
        }
    }

    fun currentZoomForTest(): Double = map?.cameraPosition?.zoom ?: -1.0

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun noteFeature(note: OsmNote): Feature =
        Feature.fromGeometry(Point.fromLngLat(note.lon, note.lat)).apply {
            addNumberProperty("note_id", note.id)
        }

    private fun showNote(note: OsmNote) {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 8, 18, 8)
        }
        note.comments.forEach { c ->
            box.addView(TextView(this).apply {
                val who = c.user ?: "anonym"
                text = "$who · ${c.action}\n${c.text}\n"
                textSize = 14f
                setTextColor(Color.WHITE)
            })
        }
        scroll.addView(box)

        val builder = AlertDialog.Builder(this)
            .setTitle("Hinweis #${note.id}")
            .setView(scroll)
            .setNegativeButton("Zurück", null)

        if (note.status == "open") {
            builder.setPositiveButton("Kommentar", null)
            builder.setNeutralButton("Erledigt", null)
        }

        val dlg = builder.create()
        dlg.setOnShowListener {
            if (note.status == "open") {
                dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    ensureLoginThen { askComment(note, false, dlg) }
                }
                dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    ensureLoginThen { askComment(note, true, dlg) }
                }
            }
        }
        dlg.setOnDismissListener { shownNoteForTest = null }
        dlg.show()
        shownNoteForTest = note
    }

    fun shownNoteForTest(): OsmNote? = shownNoteForTest

    private fun ensureLoginThen(action: () -> Unit) {
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token != null) action() else {
            if (BuildConfig.OSM_CLIENT_ID.isBlank()) {
                AlertDialog.Builder(this)
                    .setMessage("Zum Schreiben brauchst du einmalig eine OSM-OAuth-App. Siehe README: OSM_CLIENT_ID eintragen.")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                startActivity(Intent(this, OAuthActivity::class.java))
                Toast.makeText(this, "Nach dem Login Hinweis erneut öffnen", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun askComment(note: OsmNote, close: Boolean, parent: AlertDialog) {
        val input = EditText(this).apply {
            hint = if (close) "Optionaler Abschlusskommentar" else "Kommentar"
            minLines = 2
        }
        AlertDialog.Builder(this)
            .setTitle(if (close) "Hinweis schließen" else "Kommentar hinzufügen")
            .setView(input)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton(if (close) "Schließen" else "Senden") { _, _ ->
                val text = input.text.toString().trim()
                if (!close && text.isBlank()) return@setPositiveButton
                Thread {
                    try {
                        if (close) NotesRepositoryProvider.current.close(this, note.id, text) else NotesRepositoryProvider.current.comment(this, note.id, text)
                        runOnUiThread {
                            Toast.makeText(this, "Gespeichert", Toast.LENGTH_SHORT).show()
                            parent.dismiss()
                            loadNotesForVisibleMap(force = true)
                        }
                    } catch (e: Exception) {
                        runOnUiThread { Toast.makeText(this, e.message, Toast.LENGTH_LONG).show() }
                    }
                }.start()
            }.show()
    }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(r, p, g)
        if (r == 7 && g.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
            locateAndLoad(true)
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        startLocationUpdates()
    }
    override fun onPause() {
        stopLocationUpdates()
        mapView.onPause()
        super.onPause()
    }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }

    companion object {
        private const val OPEN_SOURCE = "osm-notes-open-source"
        private const val CLOSED_SOURCE = "osm-notes-closed-source"
        private const val OPEN_LAYER = "osm-notes-open-layer"
        private const val CLOSED_LAYER = "osm-notes-closed-layer"

        private const val OPENFREEMAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"
        private const val UI_TEST_STYLE = """{
          "version": 8,
          "sources": {},
          "layers": [
            {"id": "background", "type": "background", "paint": {"background-color": "#d8dde3"}}
          ]
        }"""
    }
}
