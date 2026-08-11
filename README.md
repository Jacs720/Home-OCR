# Pokémon HOME OCR

Aplicación Android que registra una colección de Pokémon HOME desde la ficha individual. El análisis combina PaddleOCR local con detectores visuales para Poké Balls, marcas de origen, shiny y formas alternativas. Las capturas se procesan en memoria y la colección se exporta como CSV UTF-8.

## Funciones principales

- Lectura directa de la pantalla mediante `MediaProjection`, sin guardar screenshots.
- Control flotante sobre Pokémon HOME para capturar cada ficha.
- Swipe automático opcional mediante Shizuku, sin servicio de Accesibilidad.
- Límite configurable de Pokémon por sesión.
- Pokédex local de 1025 especies e identificación prioritaria por número nacional.
- Idiomas de HOME: JPN, ENG, SPA/ES-ES/ES-LA, FRE/FRA, GER/DEU, ITA, KOR, CHS y CHT.
- Detección de Poké Ball, marca de origen, shiny, OT e IDNo.
- Formas regionales, Unown y varias formas distinguibles por tipos, estadísticas o apariencia.
- Persistencia local y exportación de la colección a CSV.
- Checklist Shiny con 7.714 combinaciones posibles de especie/forma, marca de origen y bonus.
- Sincronización automática del checklist con la colección OCR local e importación de CSV externos.

La aplicación Android actual es la versión **0.6.0**. El APK de prueba generado localmente queda en `dist/PokemonHomeOCR-debug.apk`; la carpeta `dist` está excluida de Git para que los binarios se publiquen como Releases de GitHub.

## Compilar Android

Requisitos:

- JDK 17;
- Android SDK 35 y Build Tools 35;
- Android NDK `21.4.7075529`;
- CMake `3.10.2`.

En una ruta sin caracteres especiales:

```powershell
cd android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Si la ruta de Windows contiene caracteres como `Pokémon`, usa el script incluido. Crea temporalmente una unidad con ruta ASCII, ejecuta pruebas, lint y build, y copia el APK a `dist/`:

```powershell
.\android\build-windows.ps1
```

Paddle Lite, OpenCV y los modelos OCR se descargan durante el primer build y no se versionan.

## Uso

1. Abre la app y define cuántos Pokémon se leerán.
2. Si deseas avance automático, inicia Shizuku y autoriza la conexión desde la app.
3. Pulsa **Iniciar lectura sobre Pokémon HOME** y concede superposición y captura de pantalla.
4. Abre una ficha en Pokémon HOME. La lectura se repite hasta alcanzar el límite.
5. Si Shizuku no está disponible, usa el botón flotante **Capturar** manualmente.
6. Regresa a la app para revisar la colección y exportar el CSV.
7. Abre **Checklist Shiny** para sincronizar la colección local, importar otro CSV, buscar pendientes o marcar excepciones manualmente.

El catálogo inicial procede de `Origin mark list (Shiny).xlsx`. Durante la importación se corrigieron seis erratas evidentes de nombre y la fila de Cutiefly, que figuraba como #712 en lugar de #742; el Excel original no se modifica.

## Privacidad y permisos

El APK no solicita almacenamiento, contactos, ubicación, micrófono ni Accesibilidad. Android muestra dos permisos visibles porque son necesarios para trabajar sobre Pokémon HOME:

- **Mostrar sobre otras apps**, para el control flotante.
- **Capturar pantalla**, autorización temporal del sistema para analizar la ficha en memoria.

Shizuku es opcional y su autorización puede revocarse desde su propia aplicación.

## Recursos gráficos

El arte original del icono se conserva en `branding/app_icon_source.png`. Para regenerar todos los tamaños legacy, redondos y adaptativos:

```powershell
python tools/generate_android_icons.py
```

El script solo recorta transparencia, escala la imagen original y la coloca sobre el fondo de marca; no redibuja el arte.

## Prototipo y herramientas

El directorio `src/` contiene el prototipo de escritorio y `tools/` utilidades de calibración. Para instalar sus dependencias sin versionar un entorno virtual:

```powershell
py -3.12 -m venv .venv
.\.venv\Scripts\pip.exe install -r requirements.txt
```

Las capturas de prueba, CSV personales, SDK local, entornos virtuales, dependencias Android descargadas y artefactos de build están excluidos por `.gitignore`.

## Límites conocidos

- El APK actual incluye únicamente `arm64-v8a`.
- Las regiones visuales están calibradas para la ficha vertical de Pokémon HOME usada en las pruebas.
- Algunas formas puramente visuales todavía necesitan más referencias; entre ellas están los Pikachu con gorra.
- Cuando la confianza de una forma es insuficiente, se guarda para revisión en lugar de asumir la forma estándar.

## Licencia

El código propio se distribuye bajo la licencia MIT incluida en `LICENSE`. Consulta `android/THIRD_PARTY_NOTICES.md` para las dependencias y datos de terceros.
