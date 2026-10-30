# Feature: app-contingencia-qa

Objective: implement the Android app fixes defined in `docs/PLAN_APP.md` (A1, A2, M2, M1, B1, B2, B7, B8-text, B9, B5).
Branch: `fix/app-contingencia-qa` (from the T0 baseline). Delivery strategy: `ask-on-risk`, work-unit commits about 400 lines, Conventional Commits, no AI attribution.
Test runner: `bash gradlew testDebugUnitTest` (JVM unit tests; no Compose UI test or Robolectric dependency today). Device checks via wireless ADB.

## Specs

Source of truth: `docs/PLAN_APP.md`. Quoted fragments are verbatim.

- **S0 Baseline.** "Inicializar Git en `dlecheymielapp` (no tiene metadatos) o fijar una copia de referencia del código probado."
- **S1 A1.** "Hacer desplazables los estados vacíos y de error dentro de `PullToRefreshBox`" and "Agregar un botón visible **Actualizar** además del gesto."
  - Acceptance: "Una cola vacía recibe una asignación y la muestra al actualizar, sin reiniciar la app."
- **S2 A2 refresh.** "Refrescar en `ON_RESUME` (`LifecycleResumeEffect`) y al activar cada pestaña del admin." and "Unificar el regreso desde el detalle, incluido el botón Atrás del sistema."
  - Acceptance: "Volver del fondo y cambiar de pestaña actualizan los datos." and "No hay polling en segundo plano ni requests duplicadas por recomposición."
- **S3 A2 robustness.** "Evitar requests superpuestas; si una actualización falla, conservar filtros, posición y datos anteriores.", "Mostrar la hora de la última actualización y un aviso de datos desactualizados.", "Cancelar los trabajos al cerrar sesión".
  - Acceptance: "Una caída de red no convierte una lista con datos en una lista vacía."
- **S4 A2 polling.** "Polling **solo con la pantalla activa** en las colas operativas, propuesta inicial cada 60 s. **No activarlo hasta que el backend cierre M3**".
  - Assumption: implement behind a flag that defaults to off.
- **S5 M2.** "Mantener bloqueado el pedido hasta que termine la recarga posterior al avance", "Pedir confirmación explícita para **En camino** y para los estados terminales, mostrando el código del pedido y el estado destino.", "Aplicar lo mismo a los avances individuales (admin, coordinador, encargados) y a los de grupo de recojo.", "Ante timeout o conflicto, consultar el estado del pedido antes de habilitar otro intento.", "**No usar un retardo de milisegundos como única protección**".
- **S6 M1.** "excluir `sharedpref/dleche_session_cookies.xml` en `<cloud-backup>` y `<device-transfer>`. Revisar `backup_rules.xml` (Android 11 o menos) y el manifiesto fusionado del release."
- **S7 B1.** "Sin cambios de código. Verificar en el APK candidato."
  - Acceptance: "Un 401 durante el uso muestra "Su sesión venció. Ingrese de nuevo."; un logout voluntario no lo muestra".
- **S8 B2.** "Pasar "Cerrar sesión" a un elemento compartido del `AdminHost`, conservando la confirmación."
- **S9 B7.** "Validar el teléfono del proveedor con la misma regla que defina el backend (E.164, `\+[1-9]\d{7,14}`) y mostrar el error en el campo."
  - Acceptance: "`abc` muestra error y no se envía".
- **S10 B8 text.** "Deshabilitar "Enviar" o mostrar "Describa la incidencia" cuando el texto está vacío.", "Conservar el borrador si falla el envío.", "Mantener el límite de 2000 caracteres".
  - Out of scope: the photo, which is blocked by the backend API.
- **S11 B9.**
  - "Un solo mensaje al validar el código"
  - "Habilitar "Cobrar con QR o link" según `linkAvailable`"
  - "Excluir la franja actual de "Reasignar a otra franja""
  - "Leer `pricingSnapshot` … **No calcular el costo restando importes ni usar la tarifa vigente.**"
  - "Mostrar `refundStatus`"
  - "Corregir plurales … con recursos de texto y `pluralStringResource`."
- **S12 B9 commercial.** "Leer `commercialSnapshot` para preparar packs, combos y personalizaciones."
- **S13 B5.** "Activar optimización y ofuscación … con reglas para kotlinx-serialization, Retrofit, OkHttp y ZXing." and "Probar … **sobre el APK release**", "Guardar el `mapping.txt`".
- **S14 Closing.** Device matrix of §6 for the 5 roles and B1 verification on the candidate APK.

## Tasks

Routes: **P** = parent (Opus) inline; **H** = Haiku writer; **S** = Sonnet verifier. Tests are written with the code, in the same unit.

| ID | Specs | Route | Depends | Risk | Status | Commit |
|---|---|---|---|---|---|---|
| T0 | S0 | P | — | none | pending | — |
| T1 | S1, S2, S3 | H (unit "lists") | T0 | none | pending | — |
| T2 | S5 | H (unit "advance"), parallel with T1 | T0 | item 4 (async/concurrency) → S verifier | pending | — |
| T3 | S4 | H, after T1 | T1 | none (flag off) | pending | — |
| T4 | S6, S8, S9, S10 | H (unit "session & forms") | T0 | item 2 (backup/credentials) → S verifier | pending | — |
| T5 | S11 | H (unit "detail info") | T2 (shares detail screens) | none | pending | — |
| T6 | S12 | H | T5 | none | pending | — |
| T7 | S13 | H, then S on the release APK | T1-T6 | item 5 (release build) → S verifier | pending | — |
| T8 | S7, S14 | S (device E2E via ADB) | T1-T7 | — | pending | — |

**Parallel plan.** Wave 1 runs T1, T2 and T4 at once, with disjoint edit surfaces.
- T1: list screens, list ViewModels and the hosts' refresh wiring.
- T2: `CardActions.kt`, the detail ViewModels and the confirmation dialogs.
- T4: XML rules, the AdminHost top bar, the provider form and the incident dialog.
- Shared files (`AdminHost.kt`, `HandlerHost.kt`): owned by T1. T4's logout change goes in a new shared component that T1 wires in, or the parent edits it at the end.

Wave 2 runs T3 and T5, wave 3 runs T6 and T7, and T8 closes.

**Blocked outside the app.** S4 activation waits for backend M3. The B8 photo waits for a new backend API.

## Log

- **L1 (2026-10-30, user, verbatim):** "ahora vamos a trabajar principalmente con @dlecheymielapp/docs/PLAN_APP.md antes de actuar haz un plan detallado de tareas. utiliza odd de gentleAI usando engram para guardar memoria. tiens acceso a todos los modelos. haiku para tareas e implementacion, sonnet para validaciones, opus para planificacion. usa toda la capacidad para optimizar el trabajo."
- **L2:** exploration. The app has no `.git`, so CodeGraph cannot initialize until T0. There are 7 JVM test files (repositories, `CardActionsTest`, `QrCodeTest`) and no Compose UI test dependency. Every list ViewModel already exposes `refresh()`; `AdminHost` keeps the tab in local state, and `HandlerHost` uses `BackHandler`. `linkAvailable` already exists in `FulfillmentModels.kt:61`. `gradlew` lacks its exec bit, so run it with `bash gradlew`.
- **L3:** plan written; awaiting user approval before T0.
- **L4:** Engram mirror **pending**: `mem_save` was rejected by a hook (`host session ... already ended`). Resync to topic `odd/app-contingencia-qa/tasks` when Engram accepts writes.
