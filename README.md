<div align="center">

# 📅 Expressive Calendar

**A calendar for Android built with Material 3 Expressive.**

Soft morphing shapes, frosted glass, variable type, and your events from the calendars already on your phone.

<br/>

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-M3%20Expressive-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-F9AB00?style=for-the-badge)

</div>

---

## ✨ Highlights

| | |
|---|---|
| 🗓️ **Three views** | **Month**, **Agenda** and **Day**, one tap apart on a floating glass dock |
| 🔄 **Uses your existing calendars** | Reads and writes through Android's `CalendarContract`, so Google, Samsung and local calendars all sync through the accounts you already have. No new account, no cloud of its own |
| ✏️ **Event editor** | Cancel/Save bottom sheet with date and time pills, repeat rules, reminders and a calendar picker |
| 🎉 **Public holidays** | Fetched from [Nager.Date](https://date.nager.at) for any country and cached per country and year |
| 🧩 **Home screen widget** | The month at a glance, with holidays and events |
| 🎨 **10 colour themes** | Wallpaper-based dynamic colour plus 9 presets: *Concept, Violet, Coral, Ember, Citron, Forest, Lagoon, Azure, Fuchsia* |
| 🌗 **Dark mode** | System, Light or Dark, with an optional pure-black AMOLED mode |
| 🔤 **Google Sans Flex** | A variable font with optical sizing, so headings look right at every size |
| 🍪 **Expressive shapes** | `MaterialShapes` (Cookie, Sunny, Soft Burst) and springy press feedback throughout |

## ⚙️ Settings

- **Week starts on:** system default, Saturday, Sunday or Monday
- **Default view:** Month, Agenda or Day
- **Default reminder:** none, at start, or anywhere from 5 minutes to 1 week before
- **Week numbers:** on or off
- **Holidays:** on or off, and which country (searchable picker with flags)
- **Calendars:** choose which of your device calendars to show

## 🔐 Permissions

| Permission | Why |
|---|---|
| `READ_CALENDAR` / `WRITE_CALENDAR` | To show, create and edit events in your device calendars |
| `INTERNET` | Only to download public holiday lists from `date.nager.at` |

There are no analytics, no ads and no trackers.

## 🚀 Getting started

### Requirements

- Android Studio (recent canary or preview that supports **AGP 9.2**)
- JDK 17+
- A device or emulator running **Android 8.0 (API 26)** or later

### Build and run

```bash
git clone https://github.com/Najeer-k11/expressive-calendar.git
cd expressive-calendar
./gradlew installDebug
```

The debug build installs as `app.expressivecalendar.debug`, so it can sit next to a release build.

### Release build

Signing is read from a git-ignored `keystore.properties` in the project root:

```properties
storeFile=/path/to/release.jks
storePassword=••••••
keyAlias=••••••
keyPassword=••••••
```

```bash
./gradlew assembleRelease
```

If `keystore.properties` is missing, the release build is produced unsigned. R8 minification and resource shrinking are on.

## 🏗️ Project structure

```
app/src/main/java/app/expressivecalendar/
├── MainActivity.kt
├── data/
│   ├── CalendarRepository.kt   # CalendarContract reads and writes
│   ├── HolidayRepository.kt    # Nager.Date fetch + on-disk cache
│   └── Settings.kt             # DataStore-backed preferences
├── ui/
│   ├── CalendarApp.kt          # App scaffold and navigation
│   ├── CalendarViewModel.kt
│   ├── CalendarDock.kt         # Floating glass dock
│   ├── Glass.kt                # Frosted glass backdrop
│   ├── Components.kt
│   ├── screens/                # Month, Agenda, Day, Settings, Permission
│   ├── sheets/                 # Event editor and detail sheets
│   └── theme/                  # Palettes, typography, MaterialExpressiveTheme
└── widget/
    └── MonthWidgetProvider.kt  # Home screen month widget
```

## 🧰 Tech stack

- **Kotlin 2.4** and **Jetpack Compose** (alpha Compose BOM `2026.07.01`)
- **Material 3 `1.5.0-alpha25`**, which provides the expressive components
- **AndroidX Graphics Shapes** for shape morphing
- **DataStore Preferences** for settings
- **AGP 9.2** with built-in Kotlin support

> [!NOTE]
> The project uses the **alpha** Compose BOM on purpose. Stable Material 3 ships the expressive theming but not the expressive components, and mixing an alpha `material3` with the stable BOM pulls in mismatched Compose versions. See `gradle/libs.versions.toml` for details.

## 🙏 Credits

- **[Rayane Bouafia](LICENSE)**, for the original *Expressive Clock* design system and theming this app is built on
- **Google**, for [Material 3 Expressive](https://m3.material.io/) and the [Google Sans Flex](https://fonts.google.com/specimen/Google+Sans+Flex) typeface (SIL OFL 1.1)
- **[Nager.Date](https://date.nager.at)** by Tino Hager, for the free public holiday API
- **The Android Open Source Project** and **JetBrains**, for Jetpack Compose, AndroidX and Kotlin

See [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for full attributions.

## 📜 License

Released under the [MIT License](LICENSE).

Bundled fonts are under the SIL Open Font License 1.1. See [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) and [`licenses/`](licenses/).

<div align="center">
<br/>
<sub>Made with ☕ and Jetpack Compose</sub>
</div>
