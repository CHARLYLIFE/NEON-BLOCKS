# NEON BLOCKS — Android

Juego de bloques manual para Android. Las piezas **no caen**: el usuario las arrastra o las selecciona y toca una posición del tablero 10×10.

## Incluye

- Cuatro modos funcionales: Clásico, Desafío (2.000 puntos), Contrarreloj (90 segundos) y Diario (1.500 puntos).
- 29 geometrías de piezas, tres piezas disponibles por turno y generación de un nuevo lote al consumirlas.
- Colocación manual con vista previa válida/no válida durante el arrastre, ajuste a la cuadrícula y alternativa seleccionar + tocar.
- Limpieza simultánea de filas y columnas, combos y puntuación.
- Un deshacer por partida, guardado local automático, récords por modo y estadísticas.
- Efectos dibujados por Canvas, partículas al limpiar líneas, tonos sintéticos, música ambiental original en bucle (opcional), vibración y modo de efectos reducidos.
- Sin Internet, anuncios, analítica ni permisos sensibles.
- Pruebas unitarias de reglas esenciales y GitHub Actions.

## Requisitos

- Android Studio compatible con Android Gradle Plugin 8.13.x.
- JDK 17.
- Android SDK Platform 36.
- Gradle 8.13.

`minSdk = 26` y `targetSdk = 36`. Google Play exige actualmente API 36 o superior para nuevas aplicaciones y actualizaciones de apps móviles desde el 31 de agosto de 2026. Revisa siempre Play Console antes de publicar.

## Abrir y compilar

1. Descomprime el ZIP y abre la carpeta `NEON-BLOCKS-ANDROID` en Android Studio.
2. Instala Android SDK Platform 36 cuando Android Studio lo solicite.
3. Espera a que termine la sincronización de Gradle.
4. Ejecuta `app` en un dispositivo/emulador o usa **Build > Build Bundle(s) / APK(s)**.

Desde una terminal con JDK 17 y Android SDK configurado:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

El script `gradlew` de este paquete es un bootstrap ligero (no el wrapper estándar con JAR): utiliza Gradle 8.13 si está instalado o descarga esa distribución en el primer uso. En Windows, usa `gradlew.bat`.

## GitHub Actions

Al subir a `main` o `master`, el workflow `.github/workflows/android.yml` ejecuta pruebas, genera un APK debug y un AAB release como artefactos de la ejecución.

### Firma de lanzamiento

Para que el AAB sea firmado, crea una clave de carga/firmado en tu equipo y configura estos GitHub Actions Secrets:

- `RELEASE_KEYSTORE_BASE64`: archivo `.jks` codificado en Base64.
- `RELEASE_STORE_PASSWORD`: contraseña del almacén.
- `RELEASE_KEY_ALIAS`: alias de la clave.
- `RELEASE_KEY_PASSWORD`: contraseña de la clave.

En Linux/macOS, para generar Base64 sin saltos de línea:

```bash
base64 -w 0 neon-release.jks
```

En macOS, `base64 neon-release.jks | tr -d '
'`.

Puedes crear un almacén con `keytool -genkeypair -v -keystore neon-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias neonblocks`. Nunca subas el `.jks` ni las contraseñas al repositorio. Sin los cuatro Secrets, GitHub Actions genera un AAB **sin firmar**, útil como artefacto de compilación pero no listo para Play Console. Descarga el AAB generado y comprueba su firma antes de subirlo a Play Console. Conserva una copia de seguridad segura de la clave; perderla puede impedir actualizaciones futuras si no tienes habilitada la gestión de claves de Play App Signing.

## Personalización antes de publicar

- Cambia `applicationId` en `app/build.gradle.kts` por un identificador de paquete que controles y que no esté ocupado.
- Actualiza nombre, versión, ficha de Play y política de privacidad si cambia el comportamiento o se incorporan SDKs externos.
- Añade capturas reales de un dispositivo Android y comprueba la ficha en distintos formatos. No uses mockups que no representen la app publicada.
- Completa clasificación de contenido, seguridad de datos y demás formularios de Play Console.
- Ejecuta pruebas manuales en varios tamaños de pantalla, Android 8.0 o superior, pausa/reanudación, modo avión, llamadas y cierre forzado.

## Privacidad

La versión incluida funciona localmente, no solicita permiso de Internet y guarda partida, récords, ajustes y estadísticas en preferencias privadas de la app. La plantilla `PRIVACY-POLICY.md` describe ese comportamiento. Si se agregan anuncios, cuentas, telemetría o servicios remotos, hay que actualizarla y completar de nuevo el formulario de Seguridad de datos.

## Estructura

```text
NEON-BLOCKS-ANDROID/
├── app/
│   ├── src/main/java/com/neonblocks/game/
│   │   ├── MainActivity.kt
│   │   ├── data/       # modelos, persistencia y codec
│   │   ├── game/       # reglas puras del tablero
│   │   └── ui/         # menú, tablero, controles y pantallas
│   ├── src/main/res/   # tema e icono adaptativo
│   └── build.gradle.kts
├── .github/workflows/android.yml
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew
└── README.md
```

## Estado de validación

El código y la estructura se han generado en este entorno, pero aquí no hay Android SDK ni Gradle instalado y no se pudo ejecutar una compilación Android real. El primer resultado verificable de compilación será el de Android Studio o GitHub Actions. No se debe considerar todavía como una compilación certificada ni como aprobación de Google Play.
