# ARsens Android UI Kit

Ready-to-copy Jetpack Compose UI kit for the custom ARsens screens.

## What is included

- AR camera overlay UI
- Transparent glass menu buttons
- Plane selector with unfolded transformer schema
- 5x5 coordinate grid selector
- Manual tag coordinate input
- Placed tags list
- Placed sensors list
- Report screen with Export, Open 2D, Open 3D, Open STL actions
- 2D overview screen
- 3D overview screen mock UI
- Simple vector icons/drawables

## How to use

1. Copy `app/src/main/java/com/arsens/ui` into your Android Studio project.
2. Copy `app/src/main/res/drawable` into your project's `res/drawable` folder.
3. Make sure Jetpack Compose is enabled.
4. Add Material Icons Extended if you want all icons:

```gradle
implementation "androidx.compose.material:material-icons-extended"
implementation "androidx.compose.material3:material3"
```

5. Use one of these composables:

```kotlin
ARSensOnTheFlyScreen()
ARSensTagMenuScreen()
ARSensReportScreen()
ARSens2DViewScreen()
ARSens3DViewScreen()
```

## Notes

This kit is UI-only. Camera, AR tracking, AprilTag detection, STL loading, exports and real sensor/tag data should be connected in your app layer.
