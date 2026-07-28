# Kart Telemetry

App Android (Kotlin + Jetpack Compose) para convertir un móvil Android antiguo
en un display de telemetría montado en el kart: velocidad por GPS, fuerza G
(lateral/longitudinal) por acelerómetro, cronómetro de vueltas y grabación de
sesión en CSV.

No depende de Google Play Services (usa `LocationManager` en vez de
`FusedLocationProviderClient`), así que funciona en móviles antiguos o sin
Play Services actualizado.

## Requisitos

- Android Studio (Iguana o más reciente recomendado).
- Un móvil con Android 8.0 (API 26) o superior.
- GPS activado en el móvil.

## Cómo compilar e instalar

1. Abre la carpeta `kart-telemetry-app/` con Android Studio (`Open` →
   selecciona esta carpeta). Se sincronizará solo.
2. Conecta el móvil por USB con la depuración USB activada, o usa
   `./gradlew installDebug` desde esta carpeta con el móvil conectado.
3. También puedes generar el APK desde Android Studio: `Build > Build APK(s)`,
   y luego copiar el APK al móvil e instalarlo manualmente (activa "orígenes
   desconocidos" si hace falta).

Este proyecto no se ha compilado en el entorno donde se generó porque el
sandbox no tiene acceso al repositorio Maven de Google — Android Studio en tu
máquina sí lo tendrá y debería compilar sin cambios.

## Montaje recomendado en el kart

- Móvil en modo **horizontal (landscape)**, fijo y rígido respecto al chasis
  (un soporte que vibre o se mueva falsea las lecturas de G).
- Pantalla siempre encendida mientras la app está abierta (la app ya fija
  `FLAG_KEEP_SCREEN_ON` y oculta las barras del sistema).
- La orientación exacta de montaje (plano, inclinado, boca abajo) no importa
  para la medición de G: la app usa el sensor de rotación del móvil para
  proyectar la aceleración al plano horizontal real, independientemente de
  cómo esté encarado el teléfono.

## Qué muestra el dashboard

- **Velocidad**: instantánea (GPS) y velocidad máxima de la sesión.
- **Fuerza G**: G lateral y longitudinal en tiempo real (relativas a la
  dirección de marcha, calculadas con el rumbo GPS), con un "g-meter"
  circular superpuesto sobre la trayectoria GPS grabada de la sesión (el
  trazado del circuito, con un punto verde marcando la posición actual y
  uno amarillo la línea de meta si la has fijado), y los picos de la
  sesión.
- **Vueltas**: cronómetro de la vuelta en curso, última vuelta, mejor vuelta
  y número de vueltas. Botón grande "VUELTA" para marcar manualmente.
- **Grabación**: botón para grabar la sesión completa a un CSV en el propio
  móvil (velocidad, G, posición, vuelta), para analizarla después en el
  ordenador.

## Cronometraje automático de vueltas

1. Coloca el kart parado exactamente en la línea de meta.
2. Pulsa "Fijar meta" en la barra superior — guarda esa posición GPS.
3. A partir de ahí, cada vez que el kart pase cerca de ese punto (radio de
   ~15 m) se marca una vuelta automáticamente, con un margen de recodo de
   40 m para no contar dos veces el mismo cruce.

Es una detección por proximidad, no por línea exacta, así que la precisión
depende del GPS del móvil (normalmente unos pocos metros). Para circuitos
muy pequeños o vueltas muy cortas, el botón manual "VUELTA" es más fiable.
También puedes usar solo el botón manual sin fijar meta nunca.

## Dónde quedan las grabaciones

Los CSV se guardan en el almacenamiento privado de la app:
`Android/data/com.ander.karttelemetry/files/sessions/`. Puedes acceder con
un gestor de archivos, o conectar el móvil por USB y copiarlos al
ordenador.

## Limitaciones conocidas

- La fuerza G asume que el kart se mueve por un plano razonablemente llano;
  no compensa desniveles del circuito.
- La descomposición lateral/longitudinal solo es fiable con velocidad > 2
  m/s (necesita un rumbo GPS válido); parado o a muy baja velocidad se
  muestra la magnitud horizontal total en su lugar.
- El GPS de un móvil típico tiene una precisión de unos metros y una
  frecuencia de ~1-5 Hz, suficiente para velocidad/vueltas en karting pero
  no comparable a un GPS de telemetría dedicado de alta frecuencia.

## Estructura del proyecto

```
app/src/main/java/com/ander/karttelemetry/
  MainActivity.kt              actividad única, permisos, pantalla completa
  core/
    TelemetryEngine.kt         sensores + GPS + orquestación del estado
    TelemetryUiState.kt        estado inmutable que consume la UI
    LapTracker.kt              detección de vueltas (manual + por GPS)
    SessionRecorder.kt         escritura del CSV de sesión
  ui/
    DashboardScreen.kt         pantalla principal (Compose)
    PermissionScreen.kt        pantalla de solicitud de permiso de ubicación
    theme/Theme.kt             tema oscuro de la app
```

## Ideas para siguientes iteraciones

- Exportar/compartir el CSV directamente desde la app (Intent de compartir).
- Guardar y recuperar varias líneas de meta (varios circuitos).
- Superposición de vuelta actual vs. mejor vuelta (delta en vivo).
- Alarma sonora al superar un límite de G o de vueltas.
