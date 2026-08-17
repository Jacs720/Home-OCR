# Android build

The app uses Java, AndroidX, and a C++ layer based on the official PaddleOCR/Paddle Lite demo. On the first build, Gradle downloads Paddle Lite 2.10, OpenCV 4.2, and the mobile PP-OCRv2 models.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

On Windows, run `build-windows.ps1` when the full path contains non-ASCII characters. Paddle Lite 2.10 requires NDK 21.4; newer NDK releases are incompatible with its precompiled library.

Main components:

- `PaddleLiteOcrEngine`: OCR adapter.
- `PokemonParser`: field extraction.
- `TemplateIconDetector`: Poké Ball, origin-mark, and shiny detection.
- `FormDetector` and specialized detectors: regional and alternate forms.
- `SpeciesCatalog`: official localized species names.
- `AppLanguage`: persistent per-app language selection.
- `OrreOriginDetector`: localized National Ribbon and distant-land evidence.
- `CollectionStore`: persistence, schema migration, localized export, and confirmed deletion.
- `ChecklistActivity`: Living Dex and Ultimate Checklist, CSV synchronization, and saved-progress deletion.
- `PokemonAnalyzer`: OCR and visual-classifier orchestration.
- `ScreenCaptureService`: direct screen capture, floating controls, and the optional Orre detail pass.
- `ShizukuSwipeController` and `SwipeUserService`: horizontal and vertical gestures with the ADB shell identity.
- `MainActivity`: manual analysis, reading configuration, language selection, progress, and collection management.

## Using it over Pokémon HOME

1. Set the Pokémon limit.
2. Start Shizuku and authorize the app when automatic swiping or Orre inspection is needed.
3. Optionally enable the Pokémon Colosseum/XD origin check.
4. Tap **Start reading over Pokémon HOME** and grant overlay and screen-capture permission.
5. The app reads each summary and advances until it reaches the limit.
6. Without Shizuku, use the floating **Capture** button manually.

Frames are processed in memory and are not saved. The APK does not declare an Accessibility service.
