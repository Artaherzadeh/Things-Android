<div align="center">

# ✨ Things Android

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%2520Compose-UI-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material%203-Design-757575?style=for-the-badge&logo=materialdesign&logoColor=white)](https://m3.material.io)
[![Android SDK](https://img.shields.io/badge/Android%2520SDK-37-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)

A gorgeous, feature-rich task management application for Android inspired by the elegance and simplicity of **Things 3**, built entirely with modern **Kotlin** and **Jetpack Compose**.

</div>

---

## 🚀 Features

- 📥 **Inbox**: Quick capture and triage for thoughts, ideas, and incoming tasks.
- ☀️ **Today**: Focus on what matters right now with dedicated daily scheduling.
- 📅 **Upcoming**: Plan ahead and view scheduled tasks across dates and weeks.
- 🎯 **Anytime**: Flexible tasks that are ready to be worked on whenever you have bandwidth.
- ⏳ **Someday**: Backlog for future ideas and long-term goals.
- ✅ **Logbook**: Archive of completed accomplishments and productivity history.
- 📁 **Projects & Areas**: Organize complex workflows into dedicated projects and high-level life areas.
- 🔍 **Quick Find Overlay**: Instantly search across tasks, notes, projects, and areas.
- 📝 **Rich Notes & Markdown**: Built-in markdown formatting, editor, and sharing capabilities.
- ☑️ **Interactive Checklists & Subtasks**: Expandable task rows, nested checklist sections, and status tracking.
- ⚡ **Fluid Gestures**: Swipe-to-delete, custom reordering, drag-and-drop slots, and multi-selection mode.
- ☁️ **Cloud Sync Engine (`Things-Cloud-Logic`)**: TypeScript/Node core sync engine with robust mapping, tags, and comprehensive end-to-end testing.

---

## 📱 App Structure & Navigation

The application uses Jetpack Compose Navigation (`ThingsNavHost.kt`) and a modular UI architecture:

```text
com.things.app/
├── ui/
│   ├── anytime/      # Anytime screen & models
│   ├── area/         # Area management & lists
│   ├── components/   # Reusable UI (TaskCard, NotesEditor, PageMenu, etc.)
│   ├── home/         # Home screen & central navigation drawer/dashboard
│   ├── inbox/        # Quick capture inbox
│   ├── logbook/      # Completed tasks archive
│   ├── navigation/   # NavHost and route definitions
│   ├── project/      # Project views & checklists
│   ├── quickfind/    # Global search & overlay
│   ├── someday/      # Someday backlog
│   ├── theme/        # Material 3 colors, typography, & spacing
│   ├── today/        # Today's focus view
│   └── upcoming/     # Scheduled planning view
└── MainActivity.kt
```

---

## 🛠️ Build & Run Instructions

### Prerequisites
- **Android Studio** (Koala / Jellyfish or newer)
- **JDK 17**
- **Android SDK Platform 37** (Android Studio will prompt to install missing SDK components automatically).

### Building an APK via Android Studio
1. Open Android Studio and select **File > Open**, then choose the `Things-Android` project root folder.
2. Wait for the **Gradle sync** to complete.
3. To build a debug APK:
   - Go to **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
   - When finished, click **Locate** in the notification popup (`app/build/outputs/apk/debug/app-debug.apk`).
4. To build a release APK:
   - Go to **Build > Generate Signed App Bundle / APK > APK**, configure your keystore, and select the `release` variant.

### Running via CLI / Device
Connect an Android device or emulator with USB debugging enabled, then run:
```bash
./gradlew installDebug
```

---

## 🧪 Testing

The project includes thorough UI and instrumentation tests (`androidTest`):
```bash
./gradlew connectedAndroidTest
```

---

## 📄 Adding a New Page

Adding a new screen or view to the app is simple:
1. Create your screen Composable package under `ui/<feature>/`.
2. Define route constants in `Routes` within `ui/navigation/ThingsNavHost.kt`.
3. Add a `composable(Routes.YOUR_ROUTE) { YourScreen(...) }` destination.
4. Navigate using `navController.navigate(Routes.YOUR_ROUTE)`.

---

## 📜 License

This project is licensed under the MIT License.
