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
| T0 | S0 | P | — | none | done (baseline 69 tests green; branch `fix/app-contingencia-qa`; CodeGraph indexed) | `1a35d45` (user) |
| T1 | S1, S2, S3, S8 | H (unit "lists"), isolated worktree | T0 | none | done (unit tests green; device check pending in T8) | `7667bc7` |
| T2 | S5 | H (unit "advance"), isolated worktree, plus a parent gap fix | T0 | item 4 (async/concurrency) → S verifier | done; verified (Sonnet: B1 fixed on recheck); native review approved | `59c08fd`, `5689de7`, `4d81ce8` |
| T3 | S4 | H, isolated worktree, plus a parent release guard | T1 | none (flag off) | done (unit tests green; device check pending in T8) | `488f5f2`, `6e844af` |
| T4 | S6, S9, S10 | H (unit "session & forms"), isolated worktree | T0 | item 2 (backup/credentials) → S verifier | done; verified (S6, S9, S10 pass); native review approved | `fc131f5`, `dc601d9`, `4d81ce8` (A1) |
| T5 | S11 | H (unit "detail info"), isolated worktree, plus a parent fix | T2 | none | done (unit tests green; device check pending in T8) | `0a63e48`, `e1fe350`, `a4925b2` |
| T6 | S12 | H, isolated worktree | T5 | none | done (unit tests green; device check pending in T8) | `3b3047d` |
| T7 | S13 | H, isolated worktree, then S on the release APK | T1-T6 | item 5 (release build) → S verifier | done; static verifier pass; native review approved; device test pending (T8) | `1a03077` |
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
- **L5:** T0 done. The user created the repo (`main` @ `1a35d45 base`). Branch `fix/app-contingencia-qa` exists. Baseline: `bash gradlew testDebugUnitTest` gives 69 tests, 0 failures. User allows Codex/OpenCode for OpenAI validations and said **never use ASTRA**.
- **L6:** routing change. B2 (S8) moved to T1, because the logout lives in the shared `BrandHeader` (`ui/common/BrandComponents.kt:68`) and only `AdminOrdersScreen` receives `onLogout` from `AdminHost`. T1, T2 and T4 share files (`AdminAssignmentsScreens.kt`, `AdminListViewModels.kt`, `FieldOrderCard.kt`, `OrderDetailScreen.kt`), so each writer runs in an isolated worktree and the parent merges sequentially.
- **L7:** T1 and T4 were cherry-picked without conflicts onto `fix/app-contingencia-qa`. `bash gradlew testDebugUnitTest assembleDebug` gives 85 tests, 0 failures, and the build succeeds.
  - T1 deviations: one commit; list error states say "Actualizar"; `PickupScreens` GroupOrders was already scrollable; `HandlerListViewModel`, `AdminOrdersViewModel` and `AdminNotificationsViewModel` have no tests, because their repositories are final classes.
  - T4: the blank-incident guard, keeping the draft and the 2000-character cap already existed; tests pin them.
  - The worktrees live under `.claude/worktrees`, which is excluded in `.git/info/exclude`.
- **L8:** T2 was cherry-picked cleanly (`59c08fd`).
  - Writer report: confirmation for `IN_TRANSIT` and the terminal statuses (`isTerminal`); code handoff has no extra dialog, because the customer's code is the confirmation; group advances only go to PREPARING or READY_FOR_PICKUP, so they need no confirmation; one out-of-surface line in `HandlerHost.kt` (`onAskAdvance`).
  - Gap: `HandlerListViewModel` and `FieldListViewModel` `onChanged` did not await the reload. The parent fixed it with RED-then-GREEN (`aCardStaysLockedUntilTheListReloadAfterItsAdvanceFinishes`): `load()` returns its `Job`, and `onChanged` calls `.join()` (`5689de7`).
  - Suite: 99 tests, 0 failures; `assembleDebug` OK.
- **L9:** `gentle-ai review assess --base-ref 1a35d45 --committed-only --escalate-item 4` gives risk `high`, `review_due` true (`high_risk`); RDD is on (global). Next steps: the Sonnet independent verifier and an optional Codex read-only second opinion, then the native review preflight STATUS (consent prompt to the user).
- **L10:** Codex (OpenAI, read-only) review of the T2 diff. The parent checked both findings against the code; both are advisories.
  - Finding 1, `CardActions.settle` could leave a card busy if `onChanged()` throws. Not reproducible today: both implementations (`load().join()` and `PickupGroupViewModel.fetchGroup`) catch their exceptions and only propagate scope cancellation, when the ViewModel is already dead. Defensive `finally` planned.
  - Finding 2, `OrderDetailViewModel` swallows `CancellationException`. Its broad catches pre-date the change (7 at baseline `1a35d45`), and the detail ViewModels never cancel their own jobs. Planned: rethrow cancellation in the catches touched by T2.
  - Both hardenings go into the single correction batch after the Sonnet verifier reports.
- **L11:** Sonnet verifier results.
  - Pass: S1-S3, S6, S8, S9, S10.
  - Fail: S5, blocker B1. A second card's reload restart cancelled the first card's awaited load; `join()` on a cancelled job returns at once, so card 1 unlocked before any reload finished.
  - Advisories: A1 (untrimmed phone sent); A2-A5 low or present at baseline.
  - Single correction batch `4d81ce8`, each fix with a RED test first:
    - B1: `reloadAndWait()` follows the replacement `job` until the awaited one is current. Test `aSecondCardRestartingTheReloadDoesNotUnlockTheFirstCardEarly`.
    - A1: the trimmed phone is sent. Assertion added to `validProviderIsSentAndValidIncidentIsStillReported`.
    - Codex finding 1: `settle()` uses try/catch/finally. Test `aReloadThatThrowsStillUnlocksTheCard`.
    - Codex finding 2: `CancellationException` is rethrown in the `OrderDetailViewModel` and `AdminOrderDetailViewModel` catches (7 in each).
  - Suite: 101 tests, 0 failures; build OK. One recheck limited to B1 requested from the same verifier.
- **L12:** Sonnet recheck limited to B1 gave PASS on `4d81ce8`, with 103 tests in the scratch clone (101 plus 2 probes). Scratch removed.
- **L13:** native RDD review of committed range `1a35d45..dae1204`.
  - `.atl/` was excluded locally first, because the gentle-ai registry cache showed up as untracked.
  - Consent `granted` by the user. Lenses: risk, resilience, reliability. All three gave **no findings**; state `approved`.
  - `acknowledge-approved` returned `gentle-ai.review-acknowledged/v1`, authority `burned` (lineage `review-34f3b3c0444a1461`). The reviewed boundary advances to `dae1204`.
  - Delivery (push/PR) remains the user's decision.
- **L14:** wave 2 launched, T3 and T5 on Haiku in isolated worktrees.
  - Contract samples for T5 are in the scratchpad `samples/`. The admin order detail has `pricingSnapshot` (`items`, `total`, `internalDeliveryFee`), `refundStatus` and a per-item `commercialSnapshot`. Field-side orders carry only `payAtDelivery {paymentStatus, codeVerified, linkAvailable}`.
  - Finding for T8: `GET /api/v1/handler/orders/12` as the assigned in-person handler returns **404** after the admin reassigned the slot through the incident flow. Check whether the reassignment intentionally drops the handler assignment.
- **L15:** T3 cherry-picked (`488f5f2`).
  - It adds a `QueuePoller` plus `QueuePollingEffect` (`repeatOnLifecycle(RESUMED)`), only on the list branches of `AdminHost`, `HandlerHost` and `FieldQueueHost`, with 3 virtual-time tests.
  - Parent change `6e844af`: release hardcodes `QUEUE_POLLING_ENABLED=false`; only debug reads `dleche.queuePolling`.
  - Differential check: with the override set, debug gives `true` and release gives `false`; `local.properties` was restored.
  - Suite: 104 tests, 0 failures.
- **L16:** T5 cherry-picked (`0a63e48`, `e1fe350`).
  - Refund values found: `refund_pending`, `refunded`, `refund_rejected`.
  - Parent fix `a4925b2`: the writer paired the unnamed `pricingSnapshot` lines with order items by position. RED test `eachLineKeepsItsOwnNameAndSubtotalEvenWhenTheSnapshotListsItemsInAnotherOrder` reproduced a wrong name next to a subtotal. The breakdown lines now come from the order's own items (name, quantity, subtotal); fee and total come from the snapshot. The unused `AdminPricedLine` was removed.
  - Suite: 114 tests, 0 failures.
  - RDD assess `--base-ref dae1204`: risk medium, `review_due` false (`under_budget`); the slice stays pending.
- **L17:** wave 3 launched, T6 (commercialSnapshot) and T7 (R8 release) on Haiku in isolated worktrees with disjoint surfaces.
- **L18:** T6 cherry-picked (`3b3047d`). Suite: 121 tests, 0 failures.
  - PACK and COMBO components show "qty × productName" plus their component `summary`; "Presentación:" shows when present.
  - Gap reported **to the backend**: product-level `configuration` stores only ids (`group_<id>`/`option_<id>`, `SellableUnitCustomizationResolver.java:147,153`) with no labels in the commercial snapshot, so a customized single product cannot show its options until the backend adds a readable item-level summary.
- **L19:** user instruction (2026-10-30, verbatim): "te explico, avanza hasta donde puedas ya que mi compañero tiene que hacer el backend con las observaciones. y luego lo continuamos". The backend observations found during the app work (N1-N7) were added to `docs/PLAN_BACKEND_WEB.md` §9 (`ff37b98`).
  - N1: reassigning the slot from an incident deletes `order_handler_assignments` for the order; confirmed in the DB for order 12.
- **L20:** T7 cherry-picked (`1a03077`).
  - R8 is on and the release APK went from 12.7 MB to 1.85 MB. A keep rule for app `@Serializable` classes was needed: R8 had stripped `DesistResult`, `RestoreResult` and `Rescheduled`.
  - Integrated build: 121 tests green; `assembleRelease` OK; the mapping includes the T6 classes.
  - RDD assess `--base-ref dae1204 --escalate-item 5`: risk high, `review_due` true. The Sonnet static verifier is launched (no device available); the native review consent is still to be asked.
  - Note: the pending `under_budget` slice (T3, T5, T6) and T7 will be reviewed as one range from `dae1204`, because the committed-only selector reviews up to HEAD.
- **L21:** T7 static verifier (Sonnet): S13 is pass-with-device-pending, no blockers; 71 serializers and the enums survive R8.
  - Native review of `dae1204..9b0fb17`: consent granted; START created lineage `review-712ba9adeae28a24`.
  - All 3 reviewer captures FAILED because the usage limit was reached ("You've hit your 5-hour limit"). Transaction state: bound, collect pending.
  - **Resume:** run the exact-lineage STATUS (the provider command saved in the scratchpad `start4.json` next_transition), then relaunch only the reoffered slots. Do NOT commit before that review completes, because a new commit changes the candidate.
  - This log entry is intentionally left uncommitted for that reason.
- **L22:** the native review resumed after the usage reset; the exact-lineage STATUS reoffered the same 3 slots.
  - Resilience and reliability were captured. The first readability payload was rejected as malformed; STATUS reoffered only that slot, and its rerun opened a correction.
  - Finding R2-006 (CRITICAL, deterministic): the group-advance hint wording changed. The change itself was requested (`PLAN_APP.md:119`, S11), but the new text "pedidos que están Confirmado" broke number agreement for the CONFIRMED label.
  - Bounded correction: plan of 2 lines, commit `769722e` with "Mueve a la vez todos los pedidos en estado %1$s."; suite 121 green.
  - Targeted validation: `approved`. `acknowledge-approved` returned authority `burned` (lineage `review-712ba9adeae28a24`). The reviewed boundary is now `769722e`.
  - Non-blocking advisories, kept as follow-ups:
    - R3-pluralquantity-mismatch (`PickupScreens.kt:135`);
    - R3-slotfilter-nulls (`AdminOrderDetailViewModel.kt:138-141`);
    - R3-r8-no-release-test (`build.gradle.kts:66-71`);
    - R4-poll-refresh-overlap (`QueuePolling.kt:17-24`);
    - readability warnings R2-001..005, 007, 008.
- **L23:** remaining work, blocked or waiting for the user:
  - T8 device E2E: 5-role matrix, B1, a signed release APK on a phone, polling with the debug override, gestures. Needs wireless ADB.
  - Backend items in `docs/PLAN_BACKEND_WEB.md` §9: M3 before turning polling on, the B8 photo API, N1, N2.
  - The Engram mirror is still pending (hook error).
- **L24 (2026-10-30):** triage of the open review advisories against the code; none needs a change.
  - R3-pluralquantity-mismatch: the plural keys on `orderCount`, the noun the phrase counts ("1 de 3 pedidos pendientes", "0 de 1 pedido pendiente"); the agreement is correct.
  - R3-slotfilter-nulls: `RescheduleSlot` fields are non-null `String`s and `AdminSnapshot` defaults to `""`, so a missing snapshot excludes no real slot.
  - R4-poll-refresh-overlap: every polled `refresh()` (`HandlerListViewModel`, `FieldListViewModel`, the three `AdminListViewModels`) returns early while a load is active, so a tick never stacks on a running load.
  - R3-r8-no-release-test: runtime proof needs the phone; it stays in T8.
