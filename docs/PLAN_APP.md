# Plan de corrección — App Android de personal

- **Origen:** `QA_HALLAZGOS.md` (QA del 2026-10-29) y `PLAN_CONTINGENCIA_QA.md` (revisión con especialista).
- **Alcance:** solo la app `dlecheymielapp`. El backend y la web están en `PLAN_BACKEND_WEB.md`.
- **Estado del código:** la carpeta ya tiene Git (línea base creada, ver §7). La alineación con la API nueva del backend está en el §9.

## 1. Resumen

| ID | Riesgo | Hallazgo | Entrega | Depende de backend | Estado (2026-10-30) |
|---|---|---|---|---|---|
| A1 | Alto | Deslizar para actualizar no funciona con la lista vacía | 1 | No | ✅ Hecho (`7667bc7`); gesto verificado en el celular |
| A2 | Alto | Las listas no se recargan al volver ni al cambiar de pestaña | 1 | Sí, el polling espera a M3 | ✅ Hecho en la app (`488f5f2`, `6e844af`); polling apagado hasta M3 |
| M2 | Medio | Doble avance de estado sin confirmación | 1 | Opcional (ver §3) | ✅ Hecho (`59c08fd`, `5689de7`, `4d81ce8`); confirmación verificada en el celular |
| M1 | Medio | La cookie de sesión se transfiere entre dispositivos | 2 | No | ✅ Hecho (`fc131f5`, exclusión de backup); falta la prueba de restauración |
| B1 | Bajo | Aviso de sesión vencida | 2 (solo verificar) | No | ✅ Verificado en el APK |
| B2 | Bajo | "Cerrar sesión" solo en la pestaña Pedidos | 2 | No | ✅ Hecho (`7667bc7`) |
| B7 | Bajo | Teléfono del proveedor sin validar | 2 | Sí (regla común) | ✅ Hecho en la app (`dc601d9`, `4d81ce8`); falta la regla en el backend (N4) |
| B8 | Bajo | Incidencias: sin feedback al enviar vacía y sin foto | 3 | Sí, la foto requiere API nueva | 🟡 Parcial: validación de texto hecha (`dc601d9`); la foto espera la API |
| B9 | Bajo | Detalle de pedido y textos | 3 | No (los datos ya llegan) | 🟡 Parcial: hecho con los datos actuales (`0a63e48`, `e1fe350`, `a4925b2`, `3b3047d`); personalización legible espera N2 |
| B5 | Bajo | Release sin ofuscación | 3 | No | ✅ Hecho (`1a03077`); R8 verificado en el celular |

**Bloqueantes para distribuir la app:** A1, A2 y M2.

### Correcciones al diagnóstico de QA

- **B1 era un falso positivo de QA.** El aviso está implementado (`AuthRepository.kt:58-63`, `LoginViewModel.kt:32-36`, `LoginScreen.kt:118`). En la prueba, la salida de la pantalla se leyó recortada y el aviso, que aparece debajo de la contraseña, quedó afuera. Queda solo como verificación sobre el APK.
- **A2 es parcial:** el admin refresca algunas listas al volver del detalle con la navegación interna, pero no al cambiar de pestaña ni al volver del fondo.
- **M2:** la app ya bloquea el botón mientras la request está en curso; el problema es que lo libera en cuanto llega la respuesta, y el botón siguiente aparece en el mismo lugar.
- **B9:** el backend ya envía `pricingSnapshot`, `refundStatus` y `commercialSnapshot`, pero los modelos Android no los leen.

---

## 2. Contención mientras no esté la corrección

| Riesgo | Medida temporal | Responsable |
|---|---|---|
| A1/A2: pedidos invisibles | El admin avisa cada asignación por el canal operativo y pide confirmación de recepción. Con la cola vacía, cerrar la app por completo y volver a abrirla. Conciliar asignaciones al inicio y al cierre del turno. | Operaciones |
| M2: avances accidentales | Un solo toque y esperar la confirmación visual. Si hay un avance incorrecto, registrar pedido, estado anterior, hora y notificaciones, y corregir con el flujo de admin. | Operaciones |
| B8: incidencias sin foto | Registrar la incidencia en texto y mandar la evidencia por un canal interno autorizado, citando el código del pedido. | Operaciones |
| B7: teléfono inválido | Verificar el contacto del proveedor antes de despachar. | Coordinación |

---

## 3. Entrega 1 — Continuidad operativa (bloqueante)

### A1 + A2: actualización confiable de las listas

**Implementación**
- Hacer desplazables los estados vacíos y de error dentro de `PullToRefreshBox`: `Modifier.verticalScroll(rememberScrollState())` en la `Column`, o un `LazyColumn` de un ítem.
- Agregar un botón visible **Actualizar** además del gesto.
- Refrescar en `ON_RESUME` (`LifecycleResumeEffect`) y al activar cada pestaña del admin.
- Unificar el regreso desde el detalle, incluido el botón Atrás del sistema.
- Polling **solo con la pantalla activa** en las colas operativas, propuesta inicial cada 60 s. **No activarlo hasta que el backend cierre M3** (ver `PLAN_BACKEND_WEB.md`): si el rate limit comparte la clave entre usuarios, el polling lo empeora.
- Evitar requests superpuestas; si una actualización falla, conservar filtros, posición y datos anteriores.
- Mostrar la hora de la última actualización y un aviso de datos desactualizados.
- Cancelar los trabajos al cerrar sesión, para que la respuesta tardía de un usuario no afecte al siguiente.

**Archivos:** `ui/handler/HandlerOrdersScreen.kt`, `ui/handler/PickupScreens.kt`, `ui/admin/AdminOrdersScreen.kt`, `ui/admin/AdminAssignmentsScreens.kt`, `ui/admin/AdminNotificationsScreen.kt`, `ui/admin/AdminHost.kt`, `ui/handler/HandlerHost.kt` y los ViewModels de listas.

**Aceptación**
1. Una cola vacía recibe una asignación y la muestra al actualizar, sin reiniciar la app.
2. Volver del fondo y cambiar de pestaña actualizan los datos.
3. Con la pantalla activa, una asignación aparece en un ciclo de polling más el tiempo de respuesta.
4. No hay polling en segundo plano ni requests duplicadas por recomposición.
5. Una caída de red no convierte una lista con datos en una lista vacía.

### M2: una intención, una transición

**Implementación**
- Mantener bloqueado el pedido hasta que termine la recarga posterior al avance, no solo hasta la respuesta del POST.
- Pedir confirmación explícita para **En camino** y para los estados terminales, mostrando el código del pedido y el estado destino.
- Aplicar lo mismo a los avances individuales (admin, coordinador, encargados) y a los de grupo de recojo.
- Ante timeout o conflicto, consultar el estado del pedido antes de habilitar otro intento.
- **No usar un retardo de milisegundos como única protección**: depende del dispositivo y de la red.

**Archivos:** `ui/handler/CardActions.kt`, `ui/handler/OrderDetailViewModel.kt`, `ui/admin/AdminOrderDetailViewModel.kt`, `ui/handler/PickupGroupViewModel.kt` y los diálogos correspondientes.

**Relación con el backend:** el backend ya recibe `expectedStatus` y `targetStatus`, pero dos transiciones válidas seguidas pasan igual. La app es la protección principal.

**Aceptación:** un doble toque antes, durante y después de una respuesta rápida no produce dos avances sin una segunda intención explícita. Una acción concurrente desde otro dispositivo se resuelve sin pisar estados.

---

## 4. Entrega 2 — Sesión, navegación y validaciones

| ID | Corrección | Aceptación |
|---|---|---|
| **M1** | En `res/xml/data_extraction_rules.xml`, excluir `sharedpref/dleche_session_cookies.xml` en `<cloud-backup>` y `<device-transfer>`. Revisar `backup_rules.xml` (Android 11 o menos) y el manifiesto fusionado del release. | Un dispositivo restaurado o migrado pide login; el original conserva su sesión. |
| **B1** | Sin cambios de código. Verificar en el APK candidato. | Un 401 durante el uso muestra "Su sesión venció. Ingrese de nuevo."; un logout voluntario no lo muestra; no quedan datos de la sesión anterior. |
| **B2** | Pasar "Cerrar sesión" a un elemento compartido del `AdminHost`, conservando la confirmación. | Accesible desde Pedidos, Asignaciones e Incidencias, con limpieza de estado. |
| **B7** | Validar el teléfono del proveedor con la misma regla que defina el backend (E.164, `\+[1-9]\d{7,14}`) y mostrar el error en el campo. | `abc` muestra error y no se envía; un número válido se guarda. |

---

## 5. Entrega 3 — Evidencia, información y release

### B8: incidencias

**Validación de texto (solo app, se puede hacer ya)**
- Deshabilitar "Enviar" o mostrar "Describa la incidencia" cuando el texto está vacío.
- Conservar el borrador si falla el envío.
- Mantener el límite de 2000 caracteres alineado con el backend (`IncidentDtos.ReportRequest`).

**Foto de evidencia (depende de la API nueva del backend)**
- Selección desde cámara o galería, previsualización, quitar la foto y progreso de carga.
- Reintento sin duplicar incidencias.
- El admin ve la evidencia en el detalle de la incidencia.

**Aceptación:** texto solo, texto con foto, archivo inválido, corte de red, reintento sin duplicados y sin acceso a evidencias de pedidos ajenos.

### B9: detalle del pedido y textos

- Un solo mensaje al validar el código; hoy salen dos casi iguales.
- Habilitar "Cobrar con QR o link" según `linkAvailable` e indicar desde cuándo estará disponible.
- Excluir la franja actual de "Reasignar a otra franja", comparando la identidad completa de la franja.
- Leer `pricingSnapshot` para mostrar subtotal, costo de entrega y total históricos. **No calcular el costo restando importes ni usar la tarifa vigente.**
- Leer `commercialSnapshot` para preparar packs, combos y personalizaciones.
- Mostrar `refundStatus`, distinguiendo solicitado o iniciado de completado.
- Corregir plurales ("1 pendientes") y frases ("estén en En preparación") con recursos de texto y `pluralStringResource`.

**Archivos:** modelos en `data/fulfillment/FulfillmentModels.kt` y `data/admin/AdminModels.kt`, `ui/handler/OrderDetailScreen.kt`, `ui/handler/FieldOrderCard.kt`, `ui/handler/PaymentQrPanel.kt`, `ui/admin/AdminOrderDetailScreen.kt` y `res/values/strings.xml`.

### B5: build release

- Activar optimización y ofuscación (`optimization { enable = true }`) con reglas para kotlinx-serialization, Retrofit, OkHttp y ZXing.
- Probar serialización, Retrofit, QR y sesión **sobre el APK release**, no sobre debug.
- Guardar el `mapping.txt` de cada versión.
- Revisar la firma, el `versionCode`, la URL del API y el CAPTCHA. Hoy el release apunta por defecto a `https://dlecheymiel.pe/` salvo que `local.properties` diga otra cosa: confirmar el destino al generar el artefacto.

---

## 6. Pruebas para cerrar

### Matriz por rol

Para ADMIN, DELIVERY_HANDLER (recojo), DELIVERY_HANDLER (entrega en persona), DELIVERY y DELIVERY_COORDINATOR, probar:

- [x] cola vacía y con datos;
- [x] asignación y reasignación (la reasignación por incidencia pierde el encargado: N1, backend);
- [x] volver del fondo y cambiar de pestaña;
- [ ] pérdida de conexión;
- [x] sesión revocada;
- [ ] acceso a pedidos fuera de su alcance.

### Flujos todavía sin probar en la app

| Flujo | Qué verificar |
|---|---|
| DELIVERY con pedidos | Asignar, ver, avanzar, reportar incidencia y completar la entrega, incluida una reasignación concurrente. |
| Pago contra entrega | Código correcto sin pago, pago aprobado con el código ya validado, respuesta de pago incierta. |
| Packs, combos y personalización | Que el encargado vea lo necesario para preparar. |
| Reembolsos | Que se muestren los estados de reembolso. |

### Capas de prueba

- [x] Unitarias de reglas y estados de los ViewModels (121 tests).
- [ ] UI (Compose) para el gesto de actualizar, la navegación y el doble toque (verificado a mano en el celular, sin tests automáticos).
- [x] APK release en un dispositivo real.

---

## 7. Despliegue y reversión

**Preparación**
- [x] Inicializar Git en `dlecheymielapp` (no tiene metadatos) o fijar una copia de referencia del código probado.
- [ ] Identificar el APK exacto probado (`versionCode`, firma y URL del API). Falta la clave de firma de release.

**Orden**
1. [ ] APK piloto con la Entrega 1, **sin polling** hasta que el backend cierre M3.
2. [ ] Activar el polling después de M3.
3. [ ] Entregas 2 y 3 (código listo; falta B8 foto y N2).
4. [ ] Ampliar a todo el personal después de un turno completo sin problemas.

**Reversión**
- Tener lista una versión de recuperación firmada con la misma clave y un `versionCode` mayor. No depender de desinstalar, que borra datos.

**Disparadores de reversión:** pedidos invisibles, avances no autorizados o pérdida de acceso generalizada.

## 8. Criterios de cierre

- [x] A1, A2 y M2 pasan su aceptación en el APK candidato.
- [ ] M1 pasa la prueba de restauración y migración.
- [x] B1 queda verificado en el APK.
- [ ] Un turno piloto termina sin pedidos invisibles ni avances accidentales.

## 9. Alineación con la API nueva (2026-10-30)

La app se desarrolló contra la versión anterior que corre en el servidor de pruebas. La versión nueva (`develop` = `main` del repo principal, contrato en `docs/api/openapi.yaml` y reglas en `docs/development/REGLAS_INCIDENCIAS_Y_REEMBOLSOS.md`) cambió sobre todo las incidencias de pedidos pagados. Se auditó el contrato contra los modelos y llamadas de la app y se adaptó la app. **No se cambió ningún dominio**: `local.properties` queda como estaba (servidor de pruebas) y el release sin `local.properties` sigue cayendo en `https://dlecheymiel.pe/`; confirmar el destino al generar cualquier APK.

### Qué cambió en el contrato y cómo lo cubre la app

| Cambio de la API nueva | En la app |
|---|---|
| Incidencia con `cause` (`NEGOCIO`, `CLIENTE`, `POR_DETERMINAR`) y `escalationLevel` (0 a 3) | Modelos `IncidentCause`, `AdminIncident.cause/escalationLevel`; el detalle muestra la causa y las marcas **Vencida** (24 h) y **Reembolso recomendado** (72 h). La cola muestra la misma marca. |
| `PATCH …/incidents/{id}/cause` (nota obligatoria, máx. 500) | Botón **Fijar quién falló** con diálogo, solo en pedidos pagados (igual que la web). |
| `POST …/incidents/{id}/refund` y `POST …/orders/{id}/refund-lost-charge` | Botón **Reembolsar total** (solo causa negocio y cobro vigente) y **Reembolsar cobro tras la pérdida** en pedidos perdidos con pago aprobado; avisan de la comisión si la venta fue antes de hoy. |
| Pedido pagado: sin "Dar por perdido" salvo causa cliente; una sola reprogramación; a los 7 días solo reembolso | `incidentRules(...)` (misma tabla que `incidentRules.ts` de la web) decide qué botones se ofrecen; el servidor lo vuelve a exigir. |
| `deadlineAt` ausente cuando el pedido nunca se pierde | Ya se mostraba solo si venía; sin cambio. |
| Estado de incidencia `RESOLVED_REFUNDED` | En los dos enums de la app (antes habría salido como "Otro estado"). |
| Avisos de la campana `INCIDENT_OVERDUE`, `REFUND_SUGGESTED`, `INCIDENT_REMINDER`, `CHARGE_AFTER_LOST` | Títulos propios en la pantalla de avisos. |
| `customerPhone` en el detalle del pedido (WhatsApp con el cliente) | Botón **Contactar al cliente por WhatsApp** con incidencia abierta, también en recojo. |
| Códigos nuevos (`LOSS_NOT_ALLOWED_PAID_ORDER`, `RESCHEDULE_LIMIT_REACHED`, `RESCHEDULE_WINDOW_CLOSED`, `INCIDENT_CAUSE_REQUIRED`, `REFUND_*`, `NOTHING_TO_REFUND`, `PAYMENT_NOT_SETTLED`, …) | Mensajes en español en `toAdminMessage()`; los que indican que el pedido cambió recargan el detalle. |
| Campos que el contrato declara opcionales en `HandlerWork` (`pickup.*`, `recipient.*`, `customerName`) | Ahora tienen valor por defecto: un pedido histórico con nulos ya no rompe la lectura. |

Compatibilidad con el servidor viejo: todo campo nuevo tiene valor por defecto y los endpoints nuevos solo se llaman al pulsar su botón, así que la app sigue funcionando contra la versión anterior (sin causa ni reembolso, como hoy).

Sin cambios necesarios (verificados contra el contrato): login/sesión/CSRF, Encargado (perfil, grupos, pedidos, entrega), Delivery, Operador, asignaciones y destino del Delivery, enlace de pago, recordatorios. El captcha sigue apagado por defecto en el backend (`CAPTCHA_ENABLED=false`); si se enciende en producción, la decisión D1 de `MAPA_DE_PROCESOS.md` pasa a ser bloqueante.

### Notificaciones de pedidos nuevos por rol

- Un trabajo periódico del sistema (`JobScheduler`, cada 15 minutos, el mínimo de Android) lee la cola del rol y avisa de lo que no estaba en la revisión anterior. Se activa al iniciar sesión y se cancela, junto con lo ya avisado, al cerrarla.
- Qué avisa cada rol: **Admin**, pedidos nuevos (`CONFIRMED`) y avisos de incidencia sin leer; **Encargado de recojo**, grupos o pedidos agregados a un grupo asignado; **Encargado de entrega en persona**, **Delivery** y **Operador**, pedidos nuevos en su cola.
- La primera revisión de un usuario solo registra lo que ya hay (no avisa de toda la cola). No se guardan datos de clientes, solo claves de pedidos, y el archivo está fuera de los respaldos.
- Android 13+ pide el permiso de notificaciones al iniciar sesión. El interruptor es `ORDER_ALERTS_ENABLED` en `app/build.gradle.kts`.
- Límite conocido: **no es push en tiempo real**. El aviso puede tardar hasta 15 minutos y algunos fabricantes (Xiaomi, por ejemplo) retrasan más los trabajos en segundo plano si la app tiene ahorro de batería. Aviso inmediato requiere FCM y un endpoint de registro de dispositivo en el backend (decisión D3 de `MAPA_DE_PROCESOS.md`). Son 4 consultas por hora por usuario; no cambia el análisis de M3, pero conviene tenerlo presente al cerrarlo.

### Qué falta (no se hizo en esta ronda)

- Probar contra la API nueva levantada en local o en el servidor de pruebas cuando se actualice (se dejó fuera a pedido): causa, reembolso, reembolso tras pérdida, WhatsApp y las notificaciones en un celular real.
- Verificar el APK release firmado (solo se compiló sin firma; el lint offline no se pudo correr por falta de caché de dependencias).
- Los pendientes de las secciones anteriores que dependen del backend (M3, N1, N2, foto de incidencias) siguen igual.

## 10. Decisiones del negocio (2026-10-30) y su efecto en la app

La app es una extensión de la página para cada rol de campo: se siguen las mismas reglas que la web. Detalle y pedidos al backend en `PLAN_BACKEND_WEB.md` §10.

| ID | Decisión | Estado en la app |
|---|---|---|
| M5 | El código de entrega se puede ingresar en cualquier momento en que el pedido esté en estado de entrega, salvo con una incidencia abierta. | ✅ Hecho: el detalle del pedido y la tarjeta de la lista no registran la entrega con una incidencia abierta (también el "Marcar como entregado" del Operador). Con una incidencia ya resuelta se puede entregar. Solo la app lo exige hoy. |
| N1 | Reasignar lo hace el Admin, que decide si el pedido queda con el mismo encargado, otro o para después. | ✅ Hecho: tras reasignar un envío en persona se abre la elección (el anterior aparece primero con "Tenía este pedido"). |
| D1 | Opción B: exención del captcha para la app con `X-App-Client`. | ⏳ Espera al backend. Con el captcha apagado, que es el valor por defecto, la app entra sin él. |
| D3 | Habrá notificaciones. | 🟡 Hay avisos locales cada 15 minutos (§9). El aviso inmediato espera FCM y el endpoint de registro del dispositivo. |
| D4 | El Admin que se entrega un Delivery lo cierra con código y registro del pago, como el Delivery interno. | ⏳ Espera las rutas del backend; después se reutiliza la pantalla del Delivery interno. |

### Estado de las casillas de cierre

Siguen abiertas, porque no se pueden cerrar desde el código: pérdida de conexión y acceso fuera de alcance en la matriz por rol (§6); DELIVERY con pedidos, pago contra entrega, packs y combos, y reembolsos (§6); APK piloto, polling tras M3 y entregas 2 y 3 completas (§7); prueba de restauración de M1 y turno piloto (§8). Los tests unitarios suman 137.
