# AR Sens MVP

Native Android MVP in Kotlin en Jetpack Compose voor montagebegeleiding van sensoren op een grote power transformator.

## Inbegrepen

- Werkvoorbereider-modus met projectnaam, GLB-koppeling, CSV-import, sensorlijst, sensor-editing en marker-editing.
- Installateur-modus met mock kalibratie, CameraX preview, ML Kit QR/DataMatrix scanning, target-overlay, foto-placeholder en stap-voor-stap bevestiging.
- Controleur/rapport-modus met sensorstatus, afwijking in mm/cm, fotonaam en CSV/JSON export.
- Interne JSON-opslag in lokale app storage; gebruikers werken via CSV en formulieren.
- Mock assets in `app/src/main/assets/` met 32 sensoren, markers, logbestand en placeholder `transformer_model.glb`.
- Vrije QR-rondgang: operator kan geplakte sensoren scannen en een gemeten positie/resultaat opslaan.
- 2D sensorkaart en AR/3D-preview als voorbereiding op echte pose tracking en modelrendering.
- Live AprilTag-detectie met CameraX + OpenCV `DICT_APRILTAG_36h11`.
- Versimpelde veldworkflow: eerst fysieke tags/model syncen, daarna sensoren aanwijzen.

## Belangrijke code

- Dataclasses: `app/src/main/java/com/example/arsens/data/Models.kt`
- CSV import/export: `app/src/main/java/com/example/arsens/data/SensorCsv.kt`
- JSON opslag/laden: `app/src/main/java/com/example/arsens/data/JsonProjectStore.kt`
- Installatiebevestiging: `app/src/main/java/com/example/arsens/data/InstallationWorkflow.kt`
- OpenCV/ArUco TODO-stubs: `app/src/main/java/com/example/arsens/ar/PoseEstimation.kt`
- AR/3D runtime-grensvlak: `app/src/main/java/com/example/arsens/ar/ArRuntime.kt`
- Live AprilTag detector: `app/src/main/java/com/example/arsens/ar/AprilTagDetector.kt`
- CameraX AprilTag preview/analyzer: `app/src/main/java/com/example/arsens/ar/AprilTagCameraPanel.kt`
- CameraX + ML Kit scanner: `app/src/main/java/com/example/arsens/scanner/BarcodeScanner.kt`
- Compose app en schermen: `app/src/main/java/com/example/arsens/ui/ArSensApp.kt`
- Extra vrije scan, kaart en AR-preview schermen: `app/src/main/java/com/example/arsens/ui/ExtendedScreens.kt`

## Verificatie

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

De debug APK staat na build in `app/build/outputs/apk/debug/app-debug.apk`.

## Richting echte AR en 3D

De app blijft nu bewust native Android. Unity is later alleen logisch als de 3D-interactie veel zwaarder wordt dan montagebegeleiding. Voor deze toepassing is de meest directe route:

1. Gebruik `Live AprilTags` op het startscherm om echte AprilTag 36h11 markers te detecteren.
2. Koppel zichtbare tags aan het projectframe: nulpunt, X-richting en hoogte.
3. Koppel optioneel een STL/GLB model aan hetzelfde frame.
4. Gebruik `solvePnP` uit `AprilTagDetector.kt` om de transformatorpose te schatten zodra bekende markerposities zichtbaar zijn.
5. Voeg Filament/SceneView toe voor echte GLB/STL-viewer en 3D sensorpunten.
6. Voeg ARCore toe voor world tracking als markertracking alleen niet stabiel genoeg is.
7. Laat vrije QR-rondgang gemeten posities opslaan uit de echte runtime in plaats van mock offsets.

De UI praat al tegen het runtime-grensvlak via status, projectie en gemeten sensorpositie. Daardoor hoeft de werkvoorbereider-, installateur- en rapportworkflow later niet opnieuw gebouwd te worden.
