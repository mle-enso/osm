# OSM Notes Watch

Kleine native Wear-OS-App: OSM-Karte, offene OSM-Hinweise in der Umgebung, Detailansicht, Kommentar und Schließen eines Hinweises.


## Voraussetzungen
- Android Studio mit Android SDK 36
- Galaxy Watch im selben WLAN oder per Wireless Debugging erreichbar
- OSM-Konto nur für Kommentar/Schließen

## 1. OSM OAuth-App registrieren (nur fürs Schreiben nötig)
1. In OpenStreetMap einloggen.
2. `https://www.openstreetmap.org/oauth2/applications/new` öffnen.
3. Name z.B. `OSM Notes Watch`.
4. Redirect URI exakt: `http://127.0.0.1:17321/callback`
5. **Confidential application deaktivieren**.
6. Scope `write_notes` aktivieren.
7. Die Client ID kopieren.

Dann in `local.properties` zusätzlich zu `sdk.dir=...` eintragen:

    OSM_CLIENT_ID=DEINE_CLIENT_ID

Ohne Client ID ist die App trotzdem vollständig lesbar; nur Kommentar/Schließen bleibt gesperrt.

## 2. Bauen
Projekt in Android Studio öffnen, Gradle synchronisieren und `assembleDebug` ausführen.
APK danach: `app/build/outputs/apk/debug/app-debug.apk`

## 3. Auf Galaxy Watch installieren
Auf der Watch: Entwickleroptionen und Wireless Debugging aktivieren. IP/Port anzeigen lassen.

    adb pair WATCH_IP:PAIR_PORT
    adb connect WATCH_IP:DEBUG_PORT
    adb install -r app/build/outputs/apk/debug/app-debug.apk

Danach `OSM Notes` aus der App-Liste starten und Standort erlauben.

## Bedienung
- Karte startet am letzten/aktuellen Standort.
- Marker = offene OSM-Hinweise im Umkreis von grob 2 km.
- Marker antippen -> Texte/Kommentare lesen.
- `Kommentar` oder `Erledigt` -> beim ersten Mal OSM-Login auf der Watch; danach Bearbeitung direkt per API.
- `◎` lädt den Standort und die Hinweise neu.

## Hinweise
- Die App lädt nur die tatsächlich angezeigten Standard-OSM-Rasterkacheln und bietet bewusst keinen Offline-Download/Prefetch.
- OSM-Attribution ist im Kartenstil hinterlegt.
- Die Notes-Abfrage liefert nur offene Hinweise (`closed=0`).
- Der Login läuft per OAuth 2.0 Authorization Code + PKCE. Der 127.0.0.1-Redirect wird innerhalb des WebViews abgefangen; es wird kein Client-Secret im APK gespeichert.

## Gradle-Version

Dieses Projekt verwendet Android Gradle Plugin 8.13.2 und ist auf Gradle 8.13 festgelegt.
Android Studio sollte unter Settings > Build, Execution, Deployment > Build Tools > Gradle bei
"Gradle distribution" / "Use Gradle from" den Gradle Wrapper des Projekts verwenden und nicht
eine lokale Gradle-9.6-Installation.

## GitHub Actions UI tests

The repository contains `.github/workflows/ui-tests.yml` for instrumented Wear OS UI tests.

The instrumentation suite uses **JUnit 5 / Jupiter 5.14.1** via `de.mannodermaus.android-junit` **2.0.1**. There are no JUnit 4 `@RunWith`/`@Rule` test classes in `androidTest`. Espresso and UI Automator remain the Android UI interaction libraries.

The CI build uses `-PuiTestFakeNotes=true`. In that mode:

- MapLibre still renders the real map view and note layers, but with a local background style (no tile network dependency).
- `FakeNotesRepository` returns one open and one closed deterministic note around the current map center.
- The production build continues to use `OsmApi` and OpenFreeMap.

Covered UI/integration scenarios:

1. App starts, map and location button are visible, and notes are rendered.
2. The open note can be tapped and its comment text is readable.
3. Swiping the map triggers a new notes request for the new visible bounding box.
4. The location button follows the emulator GPS location. CI runs this at Berlin and then again at Hamburg.
5. A rotary encoder input changes the MapLibre zoom level.

Run locally with a running Wear OS emulator:

```bash
adb emu geo fix 13.4050 52.5200
gradle connectedDebugAndroidTest -PuiTestFakeNotes=true
```

The GitHub Actions job uses Wear OS 5.1 (`android-wear`, API 35-ext15, x86_64). On failure it uploads a screenshot, Logcat, and Android test reports as the `wear-os-ui-test-results` artifact.
