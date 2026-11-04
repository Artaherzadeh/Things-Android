# Things (Android, Kotlin + Jetpack Compose)

## Build an APK
1. Open Android Studio (Koala or newer) and choose **File > Open**, then select this `Things UI` folder.
2. Wait for the Gradle sync to finish. If it asks, install JDK 17 / the Android SDK 35 platform; Studio offers to download anything missing.
3. **Build > Build Bundle(s) / APK(s) > Build APK(s)**. When it finishes, click **locate** in the notification.
   The file is at `app/build/outputs/apk/debug/app-debug.apk`. Install it on a phone by copying it over (enable "install unknown apps"), or run the app from Studio with the green Run button.
4. For a release APK to share: **Build > Generate Signed App Bundle / APK > APK**, create a keystore, choose the `release` variant.

## Adding a page
Every page registers in `ui/navigation/ThingsNavHost.kt`:
1. Create the screen composable under `ui/<page>/`.
2. Add a constant in `Routes`.
3. Add a `composable(Routes.X) { ... }` block.
4. Navigate to it with `navController.navigate(Routes.X)`.

Rebuild the APK with the same steps above after adding pages.

## Structure
- `ui/theme` colors and text styles
- `ui/home` home screen, models and icons
- `ui/navigation` routes and nav host
