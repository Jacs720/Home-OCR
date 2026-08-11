# Build Android

La aplicación usa Java, AndroidX y una capa C++ basada en el demo oficial de PaddleOCR/Paddle Lite. En el primer build, Gradle descarga Paddle Lite 2.10, OpenCV 4.2 y los modelos móviles PP-OCRv2.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

En Windows, ejecuta `build-windows.ps1` si la ruta completa contiene caracteres no ASCII. Paddle Lite 2.10 requiere NDK 21.4; versiones modernas del NDK no son compatibles con su biblioteca precompilada.

El código propio está separado en:

- `PaddleLiteOcrEngine`: adaptador OCR;
- `PokemonParser`: extracción de campos;
- `TemplateIconDetector`: Poké Balls y marcas;
- `FormDetector` y detectores especializados: formas regionales y alternativas;
- `CollectionStore`: persistencia y exportación CSV;
- `PokemonAnalyzer`: orquestación de OCR y clasificadores visuales;
- `ScreenCaptureService`: captura directa y control flotante;
- `ShizukuSwipeController` y `SwipeUserService`: swipe opcional con identidad ADB;
- `MainActivity`: configuración, selección de archivos y exportación.

## Uso sobre Pokémon HOME

1. Indica el límite de Pokémon.
2. Para automatizar, inicia Shizuku y autoriza la conexión desde la app.
3. Pulsa **Iniciar lectura sobre Pokémon HOME** y concede superposición y captura de pantalla.
4. La lectura comienza y, si Shizuku está disponible, avanza hasta alcanzar el límite.
5. Sin Shizuku, usa **Capturar** manualmente; la sesión conserva sus resultados.

Las imágenes se procesan en memoria y no se guardan. El APK no declara un servicio de Accesibilidad.
