# Pokémon HOME OCR

An Android application that catalogs a Pokémon HOME collection from each Pokémon summary page. It combines local PaddleOCR recognition with visual detectors for Poké Balls, origin marks, shininess, and alternate forms. Screen frames are processed in memory and the collection can be exported as a UTF-8 CSV file.

## Features

- Direct screen reading through Android `MediaProjection`; screenshots are not saved.
- Floating controls displayed over Pokémon HOME.
- Optional automatic swiping through Shizuku, without an Accessibility service.
- Configurable number of Pokémon per scanning session.
- Local catalog of 1,025 species, identified primarily by National Pokédex number.
- Official species names in Spanish, English, French, Italian, German, Japanese, Korean, Simplified Chinese, and Traditional Chinese.
- In-app language selector using the official Pokémon HOME language badges. Spanish is the first-run default.
- Localized user interface, checklist, species names, and exported CSV headers and values.
- Detection of Pokémon HOME language, Poké Ball, origin mark, shiny status, OT, and ID number.
- Regional forms, Unown, and multiple forms distinguished through type, stats, or appearance.
- Automatic light and dark themes based on the Android system setting.
- Manual batch analysis with preview and independent progress feedback.
- Local persistence, CSV export, and collection deletion with confirmation.
- Living Dex and Ultimate Checklist modes, including local OCR synchronization and external CSV import.
- Optional Pokémon Colosseum/XD provenance inspection for Pokémon without an origin mark.

The Android application is currently version **0.8.0**. A locally generated test APK is copied to `dist/PokemonHomeOCR-debug.apk`; `dist` is excluded from Git so binaries can be published through GitHub Releases.

## Pokémon Colosseum/XD provenance

When **Check Pokémon Colosseum/XD origin** is enabled, the scanner uses Shizuku to scroll down on Pokémon that have no origin mark. It then searches the lower summary information for either:

- the localized National Ribbon name; or
- the localized statement that the Pokémon was first met in a distant land.

The exported CSV preserves the special origin, National Ribbon evidence, and distant-land evidence in separate columns. A National Ribbon is strong evidence of a purified Shadow Pokémon. A distant-land value is useful supporting evidence but is not unique to Orre, so the app requires the absence of an origin mark as well. A confirmed shiny Pokémon is labeled **Pokémon Colosseum** because Shadow Pokémon in Pokémon XD cannot be shiny; otherwise the conservative label is **Pokémon Colosseum/XD**.

References:

- [List of Shadow Pokémon](https://bulbapedia.bulbagarden.net/wiki/List_of_Shadow_Pok%C3%A9mon)
- [Shadow Pokémon and shiny behavior](https://bulbapedia.bulbagarden.net/wiki/Shadow_Pok%C3%A9mon)
- [Distant land](https://bulbapedia.bulbagarden.net/wiki/Distant_land)
- [Ribbon names in all supported languages](https://bulbapedia.bulbagarden.net/wiki/List_of_Ribbons_in_the_games/In_other_languages)

## Building for Android

Requirements:

- JDK 17
- Android SDK 35 and Build Tools 35
- Android NDK `21.4.7075529`
- CMake `3.10.2`

From an ASCII-only path:

```powershell
cd android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

If the Windows path contains non-ASCII characters such as `Pokémon`, use the included helper. It temporarily maps the project to an ASCII drive, runs tests, lint, and the build, and copies the APK to `dist/`:

```powershell
.\android\build-windows.ps1
```

Paddle Lite, OpenCV, and the OCR models are downloaded during the first build and are not committed to the repository.

## Usage

1. Use **Manual screenshot analysis** to choose and analyze existing screenshots.
2. Use the language badge beside the app title to choose the UI and export language.
3. Under **Automatic reading over Pokémon HOME**, set the number of Pokémon to scan.
4. Start and authorize Shizuku if automatic swiping or Colosseum/XD inspection is required.
5. Optionally enable the Colosseum/XD origin check.
6. Tap **Start reading over Pokémon HOME**, then grant overlay and screen-capture permission.
7. Open a Pokémon summary page in Pokémon HOME. Reading continues until the configured limit is reached.
8. Without Shizuku, use the floating **Capture** button manually.
9. Return to **Local collection** to review, export, or delete records.

The checklist catalog is generated from the verified `Origin mark list (Shiny).xlsx` source copy. It covers all 1,025 National Pokédex numbers, keeps continued form rows, and applies the documented shiny availability rules. The Dream Ball bonus is completed only when the record has a Dream Ball and no origin mark, matching Generation V Pokémon Dream World behavior.

## Privacy and permissions

The APK does not request storage, contacts, location, microphone, or Accessibility access. Android shows two explicit permissions required to operate over Pokémon HOME:

- **Display over other apps**, for the floating controls.
- **Screen capture**, a temporary Android authorization used to analyze the visible summary page in memory.

Shizuku is optional and its authorization can be revoked from the Shizuku app.

## Graphics

The original app-icon artwork is stored in `branding/app_icon_source.png`. To regenerate legacy, round, and adaptive Android icon sizes:

```powershell
python tools/generate_android_icons.py
```

The script trims transparency, scales the source image, and places it on the brand background without redrawing the artwork.

## Desktop prototype and tools

`src/` contains the desktop prototype and `tools/` contains calibration utilities. To install their dependencies without committing a virtual environment:

```powershell
py -3.12 -m venv .venv
.\.venv\Scripts\pip.exe install -r requirements.txt
```

Test screenshots, personal CSV files, local SDKs, virtual environments, downloaded Android dependencies, and build artifacts are excluded through `.gitignore`.

## Known limitations

- The APK currently includes only `arm64-v8a`.
- Visual regions are calibrated for the vertical Pokémon HOME summary layout used by the test devices.
- Some purely visual forms still need additional references, including cap-wearing Pikachu.
- A distant-land statement is not exclusive to Orre; National Ribbon evidence is more conclusive.
- When form confidence is insufficient, the record is marked for review instead of assuming the standard form.

## License

Original code is distributed under the MIT License in `LICENSE`. See `android/THIRD_PARTY_NOTICES.md` for third-party dependencies and data.
