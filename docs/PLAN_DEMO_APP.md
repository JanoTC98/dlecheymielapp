# Plan de implementación y verificación de la demo Android

Objetivo: asegurar la sesión, preparar un APK release firmado y verificar los flujos críticos en el celular conectado, contra el backend desplegado. Los resultados se registran al final de este documento; una prueba con servidor simulado no sustituye el E2E real.

## Orden de trabajo

| Unidad | Puntos | Implementación y aceptación |
| --- | --- | --- |
| Sesión y cookies | 1, 11 | AES-GCM con clave en Android Keystore, migración atómica del almacenamiento anterior y recuperación segura ante datos corruptos. Verificar persistencia tras relanzar, cierre de sesión y rechazo de cookies Secure en HTTP o fuera del dominio/ruta. SameSite no aporta contexto de navegación en un cliente HTTP nativo; limitar la sesión al origen de la API y conservar CSRF. |
| Distribución | 2 | `signingConfig` release con secretos externos al repositorio, fallo explícito si faltan credenciales, APK firmado verificable con `apksigner`. |
| Incidencias | 4, 7, 8 | Mostrar notas con autor y fecha, distinguir alta de nota mediante la respuesta del servidor, aviso coherente en tarjeta y detalle, conteo de abiertas separado del histórico. Probar reportes repetidos y entrega tras resolución. |
| Avisos | 5 | Reproducir ausencia del job 4101 después de reabrir; asegurar programación idempotente al volver al primer plano con sesión válida y cancelación al salir. Verificar en el JobScheduler del dispositivo. |
| Tema | 6 | Mantener tema claro para la demo, deshabilitar Force Dark y usar barras del sistema legibles aunque Android esté en modo oscuro. No publicar una paleta oscura incompleta. |
| Errores de entrega | 9, 10 | Mostrar `Retry-After` en errores 429. Los códigos nuevos específicos se integrarán al existir un contrato desplegado; no inventar identificadores ni declarar comprobado un límite que el servidor no implementa. |
| Cobertura y E2E | 3 y regresión | Tests Compose de login, validación de código, cierre y repetición de incidencias; tests reales de Keystore y JobScheduler; suite JVM y lint; instalación y recorrido con las cinco cuentas demo, contrastando escrituras con la API. |

## Contrato comprobado el 6 de noviembre de 2026

- `POST /api/v1/admin/users/operational-accounts` creó los cuatro empleados; las cinco cuentas demo iniciaron sesión correctamente. `admin@gmail.com` ya existía y se configuró su contraseña solicitada.
- `IncidentDtos.Incident` expone `notes[]` con `id`, `authorRole`, `authorName`, `text` y `recordedAt`; el servicio agrega una nota cuando hay una incidencia abierta.
- El backend usa conteo `OPEN` para `incidentCount` en las vistas operativas. Se verificará cada variante de listado antes de usarlo como compatibilidad para `openIncidentCount`.
- El servicio remoto de entrega todavía no rechaza por inicio futuro de franja ni tiene límite específico de intentos por pedido. El filtro general sí expone `429 RATE_LIMITED` y `Retry-After`.
- `notas_incidencia_sin_commit.patch` no está en este checkout; la integración se realizará contra el contrato actual y se verificará con pruebas.

## Resultados

Implementación preparada en `fix/demo-app-hardening` para su integración en `main`, organizada en unidades de sesión segura, incidencias, pruebas de interfaz, recuperación de avisos y distribución release. No se modificó código del backend.

| Punto | Resultado |
| --- | --- |
| 1. Cifrado de sesión | Completado. AES-256-GCM con IV aleatorio por escritura, clave no exportable en Android Keystore y migración del campo `cookies` al campo cifrado en una sola transacción. Datos corruptos se eliminan y requieren iniciar sesión. |
| 2. Firma release | Completado para la demo. Configuración por archivo local ignorado o variables de entorno; generación release falla si faltan credenciales. Clave local de demo creada, APK con firma v2 validada y mapping de R8 archivado. |
| 3. Tests de interfaz | Completado. Siete pruebas Compose y cuatro instrumentadas de almacenamiento seguro; E2E real adicional con las cinco cuentas y restauración de sesión después de terminar el proceso. |
| 4. Aviso desde tarjeta | Completado. El diálogo consulta las incidencias actuales y avisa si existe una abierta. El seguimiento no se presenta como un reporte independiente. |
| 5. Trabajo de avisos | Recuperación verificada. Programación idempotente al reanudar con sesión, comprobación del resultado de JobScheduler y reintento cuando el sistema detiene el trabajo. Se canceló explícitamente 4101 en el dispositivo y se comprobó su recuperación al reanudar, su existencia después de relanzar y su cancelación al cerrar sesión. No se atribuye una causa a la desaparición espontánea original, que no se reprodujo. |
| 6. Tema | Decisión implementada: tema claro. Barras legibles y Force Dark desactivado en versiones compatibles. E2E completo ejecutado con Android en modo oscuro; se restauró el modo original al terminar. |
| 7. Notas | Completado contra el servidor real: notas con autor y fecha en el detalle compartido por los tres roles y en administración. HTTP 200 confirma una nota, HTTP 201 una incidencia nueva. El caso especial de impago se explica por separado. |
| 8. Conteo abierto | Completado. `openIncidentCount` explícito prevalece sobre el histórico. Compatibilidad con `incidentCount` del despliegue actual, comprobado como conteo de `OPEN` en handler, pickup y delivery. Las incidencias resueltas no bloquean entrega. |
| 9. Código anticipado | Bloqueado por contrato del backend. El servidor todavía acepta entregas antes de la franja y no devuelve un error nuevo que se pueda mapear. El E2E confirmó esa aceptación con pedidos de demo del 7 de noviembre. |
| 10. Límite de intentos | Parte cliente preparada: todo HTTP 429 muestra la espera indicada en `Retry-After`, incluida la validación de entrega. Pendiente del servidor: límite específico por pedido/actor y su código de error. La respuesta 429 de código se probó con servidor simulado, no se afirma que el límite exista en producción. |
| 11. Cookies | Completado en el cliente nativo. Se respetan Secure, HttpOnly, host y ruta al persistir/restaurar. Las cookies se restringen al origen completo de la API, incluidos cambios de dominio/puerto por redirección. SameSite es política de navegación del navegador y no se simula como si OkHttp dispusiera de ese contexto. |

### Correcciones encontradas por el E2E

- La confirmación de la nota podía quedar fuera de vista dentro de una lista desplazada. Se movió el aviso debajo del encabezado, tanto en colas como en grupos de recojo; una prueba Compose reproduce esa posición desplazada.
- El ViewModel del detalle sobrevive a la navegación. Se agregó recarga al reabrir para mostrar notas escritas desde la tarjeta, conservando el aislamiento por sesión.
- Se libera el foco al buscar pedidos y al validar el código desde el teclado para evitar que el teclado siga cubriendo la operación siguiente.

### Evidencia de pruebas

Dispositivo: Xiaomi `23117RA68G`, Android 16, ADB inalámbrico. Backend: `https://dleche.famespinoza.net.pe/`.

- **186 tests JVM**, cero fallos.
- **11 tests instrumentados**, cero fallos, ejecutados directamente con AndroidJUnitRunner: siete Compose y cuatro Keystore/SharedPreferences.
- **E2E real de las cinco cuentas**, correcto: login/logout, recuperación de 4101, recreación de actividad, rechazo de código incorrecto, cierre con código para ambos encargados y delivery, cierre confirmado del operario, incidencia seguida por nota y lectura desde el administrador.
- **Restauración después de `am force-stop`**, correcta: preparación con sesión admin, terminación del proceso, relanzamiento con sesión cifrada y job restaurados, logout y eliminación de la cookie.
- Pedidos finales de cierre comprobados por API: **102, 103, 105 y 107**, todos `DELIVERED`. Pedidos **104, 106 y 108**: exactamente una incidencia y una nota por pedido.
- `lintDebug`, `assembleRelease`, `archiveReleaseMapping` y `git diff --check`: correctos. Lint conserva advertencias no bloqueantes.
- Limitación del ejecutor: `connectedDebugAndroidTest` terminó con código de error del host aun cuando su XML registra **11 tests y 0 fallos**. La ejecución directa de AndroidJUnitRunner termina en **`OK (11 tests)`**. No se considera que el comando Gradle haya pasado ni se atribuye una causa no demostrada a esa discrepancia.

```bash
bash gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug assembleRelease archiveReleaseMapping
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
  -e notAnnotation com.grupo4.dlecheymielapp.LiveBackendTest \
  com.grupo4.dlecheymielapp.test/androidx.test.runner.AndroidJUnitRunner
```

El test `LiveBackendE2ETest` queda excluido de la suite normal porque modifica pedidos reales de demo. Se ejecuta explícitamente por método con argumentos `demoAccounts` (JSON de `kind`, `email`, `password`) y `e2eOrders` (JSON de IDs, códigos y grupo de recojo), proporcionados desde un archivo privado fuera del repositorio. No se guardan contraseñas en el código de las pruebas. El escenario requiere pedidos desechables nuevos en cada recorrido.

### APK y firma

- APK: `app/build/outputs/apk/release/app-release.apk`, versión `1.2` (`versionCode = 2`). La versión anterior presentada al PO fue `1.0`.
- Mapping: `app/release-mappings/2/mapping.txt`, ignorado por Git.
- Certificado de demo SHA-256: `885bdf4be7388947ed8c3871e911e7bcf7e353bfdf22185e3b1e7c0d0997c971`.
- Configuración privada: `keystore.properties` y `release-signing.jks`, ambos ignorados y con permisos 600. Conservar ambos para volver a firmar esta distribución.
- Plantilla versionable: `keystore.properties.example`. Variables equivalentes: `DLECHE_KEYSTORE_FILE`, `DLECHE_KEYSTORE_PASSWORD`, `DLECHE_KEY_ALIAS` y `DLECHE_KEY_PASSWORD`.
