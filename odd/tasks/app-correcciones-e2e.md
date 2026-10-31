# Feature: app-correcciones-e2e

Objective: fix, in the Android staff app, the findings of the E2E test documented in `docs/INFORME_E2E_DLECHE.md` and planned in `plan_app.md`, each one with a plan, a test and documentation.
Branch: `fix/app-correcciones-e2e` (from `main` @ `ed581dc`). Delivery strategy: `ask-on-risk`, work-unit commits of about 400 changed lines, Conventional Commits, no AI attribution. Push, PR and merge stay with the user.
Test runner: `bash gradlew testDebugUnitTest` (JVM unit tests, no Compose UI or Robolectric dependency today). Device checks with ADB on the Xiaomi 23117RA68G against `https://dleche.famespinoza.net.pe`.

## Specs

Source of truth: `plan_app.md` (tasks M-01..M-12) and `docs/INFORME_E2E_DLECHE.md`. Quoted fragments are verbatim from the plan's acceptance criteria.

- **S1 APP-01 / M-01 (P0).** "los cuatro roles llegan a su pantalla inicial, la sesión restaurada funciona y no aparece la excepción reportada."
  - Scope: declare `ACCESS_NETWORK_STATE` once in the manifest and pin it with a regression test.
- **S2 APP-06 / M-07 (P1).** "no se solicita de nuevo un código ya confirmado; el pago pendiente sigue visible y el pedido no se presenta como entregado prematuramente."
  - Assumption: the state already arrives in `payAtDelivery.codeVerified`; no backend change.
- **S3 APP-08 / M-08 (P1).** "tras una incidencia exitosa solo se muestran mensajes vigentes; no queda «El código no es válido» asociado a una entrega que ya no está habilitada."
- **S4 APP-05 / M-02 (P1).** "un pedido del 4 de noviembre no cuenta el 31 de octubre y uno de las 18:00 no cuenta antes de esa hora."
  - Assumption (default rule, business may correct it): count an order when its status matches, the slot date is today in America/Lima and the slot has started; an order from an earlier day that is still open also counts as overdue.
- **S5 APP-02 / M-03 (P2).** "se reconoce sin ambigüedad qué importe corresponde a toda la línea; los importes y totales del servidor permanecen intactos."
- **S6 APP-03 / M-04 (P2).** "el responsable actual se conoce desde el detalle sin abrir diálogos."
  - Out of scope: assignment events in the history (depends on the backend, plan B-03).
- **S7 APP-04 / M-05 (P2).** "una nota vacía no realiza una escritura y el usuario entiende cómo continuar; una válida se guarda una sola vez y confirma el resultado."
- **S8 APP-09 / M-09 (P2).** "«Av. Los Alisos 123, Los Olivos» no incorpora un segundo «Los Olivos» y ninguna parte válida de la dirección se pierde."
- **S9 APP-13 / APP-14 (P2, new in the second E2E pass).** The yellow notice "Aún no hay pedidos listos…" stays when the only order was delivered; the message "Avanzaron 1 pedidos." has a wrong plural.
- **S10 APP-12 (P2, new).** Promotions and packs show only the final price. Assumption: show the discount only if the order data carries it; never derive it.
- **Not in this feature (documented, not forgotten):**
  - APP-10 and M-10: business decision pending.
  - SEC-01, M-11, F-10: need backend contracts (plan B-01, B-12).
  - M-06 / APP-07: closed as not reproduced (the app validates E.164 and shows "Proveedor guardado."), see Log L6.
  - M-12.1 (encrypt the session cookie): P3, decision pending.
  - APP-11: to verify against real payment states before any change.

## Tasks

Route: all inline (each unit is small and understood; no writer trigger holds). `[ ]` open, `[x]` done with observed outcome.

- [x] **T1** S1 · inline · Manifest permission + regression test · RED: `AssertionError` at `ManifestPermissionsTest.kt:16` on the original manifest; GREEN: 1 test, 0 failures · commit: see L7
- [ ] **T2** S2 · inline · Hide the code form when `codeVerified` · commit: —
- [ ] **T3** S3 · inline · Clear obsolete errors after an incident · commit: —
- [ ] **T4** S4 · inline · "Para entregar ahora" by slot · commit: —
- [ ] **T5** S5 · inline · Line subtotal label · commit: —
- [ ] **T6** S6 · inline · Current assignment in the admin detail · commit: —
- [ ] **T7** S7 · inline · Empty note feedback · commit: —
- [ ] **T8** S8 · inline · Duplicated district in the address · commit: —
- [ ] **T9** S9, S10 · inline · Notice after delivery, plural, discount display · commit: —
- [ ] **T10** closure · inline · full suite, release build check, device pass, update `docs/` and the technical report · commit: —

## Log

- **L1 (2026-10-31, user, verbatim):** "haz las correcciones siempre con un plan y documentando"
- **L2 (2026-10-31, user, verbatim, earlier in the session):** "analiza el @plan_app.md y determina, a tu criterio, en que falla o si necesita mejoras." and "por ahora, centremonos en la app, si tienes algun problema con la web o el backend apntalo y se lo mandamos al equipo correspondiente."
- **L3 (2026-10-31):** Baseline: `main` @ `ed581dc` with local changes that are not part of this feature and must be preserved: modified `AndroidManifest.xml` (the permission), deleted `docs/PLAN_APP.md`, `docs/PLAN_BACKEND_WEB.md`, `docs/PLAN_CONTINGENCIA_QA.md`, untracked `plan_app.md`, `plan_dleche.md`, `docs/INFORME_E2E_DLECHE.md`. Each commit stages only the files of its task.
- **L4 (2026-10-31):** Plan review findings that shape the order: M-07 does not depend on the backend (`codeVerified` is in `FulfillmentModels.kt`); M-02 can use a default rule instead of waiting for the business; M-12.1 is P3.
- **L5 (2026-10-31):** The E2E also showed that the repository had no test that would catch APP-01 (the crash happens at first launch). T1 adds one.
- **L6 (2026-10-31):** APP-07 (provider phone `abc`) was a false positive of the first E2E pass; verified on the phone: invalid value shows "Use el formato +51987654321" and is not sent, a valid one saves with "Proveedor guardado.".
- **L7 (2026-10-31):** T1 done. `ManifestPermissionsTest` pins that the alert job keeps its connectivity constraint (`setRequiredNetworkType` in `OrderAlertService.kt`) and that the manifest declares `ACCESS_NETWORK_STATE` exactly once. Device evidence from the E2E: with the permission, admin, operador, encargado and delivery all reach their home screen and restore the session after a restart. Not done here: clean-install vs update matrix of M-01 (to repeat in T10).
