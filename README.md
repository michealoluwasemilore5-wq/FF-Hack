# Touch Sensitivity Overlay

Native Android project for a floating touch-control overlay.

## What it does
- Requests Android overlay permission.
- Guides the user to Accessibility settings.
- Shows a movable/resizable floating control above other apps.
- Stores sensitivity from 0.25x to 3.00x.
- Stores circle size and position.
- Uses Android Accessibility gesture dispatch for transformed drag gestures.

## Important Android behavior
A normal Android app cannot change the phone's physical touchscreen sensitivity. This app changes the movement of gestures it generates through the Accessibility API.

## Build
Open this folder in Android Studio, let Gradle sync, then Build > Build APK(s).
