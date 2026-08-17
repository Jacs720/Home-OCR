# Pokémon HOME OCR

Pokémon HOME OCR is an unofficial Android companion for cataloging a Pokémon HOME collection from the summary screen. It reads each visible entry on the device, keeps a local collection, and exports the results as a UTF-8 CSV file.

The app can process saved screenshots or run as a floating reader over Pokémon HOME. Screen images are analyzed in memory and are not saved.

## Features

- Reads the National Pokédex number, species, form, origin mark, shiny status, Poké Ball, Original Trainer, ID number, and language.
- Supports manual screenshot batches and direct screen reading over Pokémon HOME.
- Offers optional automatic swiping through Shizuku, without an Accessibility service.
- Includes a local checklist for normal and shiny Pokémon organized by origin mark.
- Syncs scanned Pokémon to the checklist and also accepts compatible CSV files.
- Uses localized Pokémon names and a translated interface in English, Spanish (Spain and Latin America), French, Italian, German, Japanese, Korean, Simplified Chinese, and Traditional Chinese.
- Can inspect Pokémon without an origin mark for Colosseum/XD evidence such as the National Ribbon or a distant-land location.
- Follows the Android light or dark theme.

## Requirements

- Android 7.0 or later.
- An ARM64 Android device.
- Pokémon HOME for direct screen reading.

Automatic swiping additionally requires [Shizuku](https://shizuku.rikka.app/). Android 11 or later is recommended because Shizuku can be started through Wireless debugging without a computer. Shizuku must be running and this app must be authorized before automatic swiping or Colosseum/XD inspection can work.

Shizuku is optional. Manual screenshots and the floating **Capture** button work without it.

## Using the app

### Saved screenshots

1. Tap **Choose screenshots** and select one or more Pokémon HOME summary images.
2. Set the screenshot limit if needed.
3. Tap **Analyze**.
4. Review the local collection or export it as CSV.

### Direct screen reading

1. Choose how many Pokémon to read.
2. Tap **Start reading over Pokémon HOME**.
3. Allow the app to display its controls over other apps and approve Android’s screen-capture prompt.
4. Open the first Pokémon summary in Pokémon HOME.
5. Use **Capture** after each entry, or enable automatic swiping with Shizuku.

For automatic swiping, start Shizuku first, return to the app, tap **Connect automatic swipe with Shizuku**, and approve the request. Shizuku normally needs to be started again after the device reboots.

### Checklist

Open **Checklist** to track normal and shiny variants for each supported origin mark. Filters are available for mark, variant, ownership, species, number, and form. The checklist updates from the local OCR collection when opened; it can also be synchronized again or updated from an exported CSV.

Checklist progress stays on the device. Clearing the OCR collection does not delete checklist progress, and both can be cleared independently.

## Language

Use the badge in the upper-right corner to change the app language. The selection updates the complete interface, Pokémon species names, checklist labels, and future CSV exports.

## Privacy and permissions

Pokémon HOME OCR works locally and does not upload screenshots or collection data. It does not request contacts, location, microphone, storage, or Accessibility access.

Direct screen reading uses two Android permissions:

- **Display over other apps** shows the floating controls.
- **Screen capture** grants temporary access to the visible Pokémon HOME screen.

Shizuku authorization is only used to perform the optional swipe gesture and can be revoked from Shizuku at any time.

## Building from source

The Android build requires JDK 17, Android SDK 35, Android NDK `21.4.7075529`, and CMake `3.10.2`.

```powershell
.\android\build-windows.ps1
```

The helper runs unit tests and Android lint, builds the debug APK, and places it in `dist/`. Paddle Lite, OpenCV, and the OCR models are downloaded on the first build and are not stored in Git.

## Credits

- [PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR) and [Paddle Lite](https://github.com/PaddlePaddle/Paddle-Lite) provide the on-device OCR foundation.
- [OpenCV](https://opencv.org/) is used by the native image-processing pipeline.
- [PokéAPI](https://pokeapi.co/) provides the multilingual species-name data.
- [Shizuku](https://github.com/RikkaApps/Shizuku) enables optional swipe automation without an Accessibility service.
- The origin-mark availability catalog is adapted from the verified data maintained in the Home Checklist project.

See [android/THIRD_PARTY_NOTICES.md](android/THIRD_PARTY_NOTICES.md) for dependency and data licenses.

## Disclaimer

This is an unofficial, fan-made project. It is not affiliated with, endorsed by, or sponsored by Nintendo, Creatures Inc., GAME FREAK inc., The Pokémon Company, or The Pokémon Company International. Pokémon and related names and marks belong to their respective owners.

## License

Original project code is available under the [MIT License](LICENSE).
