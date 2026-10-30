# D'Leche y Miel — App móvil: mapa de procesos y contratos

Fecha del análisis: 2026-10-25. Alcance de la app: **Encargado, Delivery (empleado), Operador** y, del **Admin**, los módulos **Pedidos** y **Asignaciones**.
Cada afirmación lleva una marca de cuánta confianza tiene:

- **[V]** Verificada en el código del backend o en `openapi.yaml`.
- **[D]** Solo documentada (docs de `docs/development` o `docs/api`), no contrastada en código.
- **[?]** Contradicción, laguna o decisión pendiente. Ver sección 9.

---

## 0. Qué fuente manda (importante para no equivocarse)

Hay documentos viejos que **contradicen** el comportamiento actual. Orden de autoridad:

1. **Código del backend** y `docs/api/openapi.yaml` (la copia del backend es idéntica).
2. `docs/api/FULFILLMENT_ROLE_VIEWS.md` (vistas de campo, 2026-10-09) y `docs/development/PAGO_CONTRA_ENTREGA_FASE3_CORTE{1,2,3,4}.md` (2026-10-15).
3. `docs/development/PRODUCTO_RULES.md`, `PROJECT_CONTEXT.md`, `ARCHITECTURE.md`, `API_AUTH_V1.md`.
4. `docs/newdocumentation/*` (v10–v14, 30-sep a 8-oct) y los `AGENTS.md` de los repos: **obsoletos en lo operativo de campo**. No usarlos para esto.

Lo que `newdocumentation` v13 todavía dice y **ya no es cierto** [V]:

| Doc viejo dice | Realidad actual |
|---|---|
| Entrega con **foto** obligatoria | **Sin foto.** Solo código de 6 caracteres. |
| "Llegué al punto" + **4 minutos** de espera antes de incidencia | **Eliminado.** Incidencia = un solo campo de texto, sin llegada ni espera. |
| Incidencia con motivo, observación y foto opcional | Solo `description` (1–2000). |
| Mercado Pago | **Culqi** (solo tarjeta, 3DS opcional). |
| Pago contra entrega **fuera del MVP** | **Implementado** (corte 1–4), apagado por defecto: `CHECKOUT_PAYMENT_OPTIONS`. |
| Código de entrega no existe para Delivery externo | **Todas las modalidades llevan código** (`OrderPublicCodes.requiresDeliveryCode` devuelve `mode != null`). La descripción de `BusinessOrderV1.deliveryCode` en el OpenAPI está desactualizada. |
| Operador cierra siempre sin código | Solo en pedidos pagados por adelantado. En pago contra entrega el Operador **valida código** (`POST /operator/orders/{id}/handoff`). |
| Delivery no puede validar código ni reportar incidencias | Sí puede (regla de seguridad corregida en el corte 4). |

---

## 1. Roles, identificadores y alcance

| Rol de negocio | Identificador técnico | Prefijo API | Qué ve |
|---|---|---|---|
| Encargado de **recojo** | `DELIVERY_HANDLER` + `handlerType=PICKUP` | `/api/v1/handler` | Grupos de recojo (punto + franja) asignados. |
| Encargado de **entrega en persona** | `DELIVERY_HANDLER` + `handlerType=INTERNAL_DELIVERY` | `/api/v1/handler` | Pedidos de entrega en persona asignados. |
| Encargado antiguo sin tipo | `DELIVERY_HANDLER`, `handlerType=null` | `/api/v1/handler` | Se tolera: se trata como `INTERNAL_DELIVERY`. |
| Delivery (empleado de la empresa) | `DELIVERY` | `/api/v1/delivery` | Solo pedidos Delivery asignados a él. |
| Operador | `DELIVERY_COORDINATOR` | `/api/v1/operator` | Cola externa de Delivery (rol-nivel, no por persona). |
| Admin | `ADMIN` | `/api/v1/admin` | Todo. En la app: solo Pedidos y Asignaciones. |

Reglas de alcance [V]:
- Los roles de campo **no ven precios, pagos (salvo `payAtDelivery`), notas ni el código de entrega**. El cliente lo recibe por correo.
- `customerName` = nombre + inicial del apellido (ej. "Ana G.").
- Un pedido no asignado, reasignado, `LOST` o de otro usuario responde `404 ORDER_NOT_FOUND`. La app debe tratar el 404 como "ya no está en tus asignaciones" y quitarlo de la lista.
- Las cuentas de campo **las crea el Admin** (`POST /admin/users/operational-accounts`). No hay registro público ni Google para ellas.
- Un usuario tiene **un solo rol**. Cambiarlo invalida sus sesiones (`sessionVersion`).
- **Los roles de campo no pueden llamar `/api/v1/public/**`** (el backend lo bloquea con `AuthorizationDecision` negativa) [V]. La app de campo no debe depender de ningún endpoint público estando logueada.
- No existen rutas de "avanzar estado" para el Encargado: solo el Admin (o Delivery/Operador en sus pedidos) avanza `CONFIRMED→PREPARING→IN_TRANSIT/READY_FOR_PICKUP`. El Encargado ve esos estados como "esperando" [V].

---

## 2. Sesión, seguridad y red (decisiones técnicas para Kotlin)

### 2.1 Cómo se autentica [V]
- `POST /api/v1/auth/login/local` con `{email, password}` → `200 {user:{id,firstName,lastName,phone,email,role,authProvider}, expiresAt}` y cookie `DLECHE_SESSION` (HttpOnly, SameSite=Lax, **Secure=true por defecto**).
- `GET /api/v1/auth/me` devuelve la sesión actual o `401 AUTH_SESSION_INVALID`.
- `POST /api/v1/auth/logout` (requiere CSRF) → 204.
- **Duración de sesión de roles internos: 8 horas** (`SESSION_ADMIN_LIFETIME`, el clientes 30 días). La app debe manejar `401` en cualquier momento y volver al login sin perder el contexto de la pantalla.
- Rutas de rol mal asignado → `403 AUTH_FORBIDDEN`. Distinguir `401` (sesión) de `403` (rol o CSRF).

### 2.2 CSRF [V]
- Se exige solo en métodos que cambian estado **y** cuando la petición lleva la cookie de sesión.
- Flujo: `GET /api/v1/auth/csrf` (204) siembra la cookie `XSRF-TOKEN` (legible, no HttpOnly) → copiar su valor a la cabecera `X-XSRF-TOKEN` en cada `POST/PUT/PATCH/DELETE`.
- En Android: un `CookieJar` persistente en OkHttp; un interceptor que lea `XSRF-TOKEN` del jar, lo agregue a la cabecera y, si falta, haga `GET /auth/csrf` primero. El cliente web hace exactamente esto (`src/api/http.ts`).
- **Trampa:** si queda una cookie de sesión vencida en el jar, un `POST /auth/login/local` **también pide CSRF** (porque hay cookie de sesión). Mitigación: antes de loguear, `GET /auth/me`; el servidor responde 401 y borra la cookie con `Set-Cookie Max-Age=0`, y OkHttp la elimina.

### 2.3 Captcha en el login — **riesgo mayor** [V]
- `POST /auth/login/local` está protegido por **Cloudflare Turnstile** cuando `CAPTCHA_ENABLED=true` (cabecera `X-Captcha-Token`). Sin token válido: `400 CAPTCHA_FAILED` o `503 CAPTCHA_UNAVAILABLE`.
- Una app nativa no genera ese token sin un WebView. **Hay que decidir cómo se maneja** (ver sección 10, decisión D1).

### 2.4 Límites de tasa [V]
- Global: 600 peticiones/min por IP. Login: **10 intentos cada 5 min por IP**. Con muchos empleados tras la misma IP (wifi del local), este límite puede bloquear. Respuesta 429 con espera (la app debe mostrarla).

### 2.5 Red en Android [V/D]
- Producción es `https://dlecheymiel.pe/api` (mismo origen). Cookie `Secure` exige HTTPS.
- En desarrollo local el backend debe correr con `SESSION_COOKIE_SECURE=false` y la app necesita permitir tráfico en claro hacia el host de desarrollo (emulador: `10.0.2.2`) con un `network_security_config` **solo en el build debug**.
- CORS no aplica a apps nativas.
- Las respuestas llevan `Cache-Control: no-store` en varias rutas; no cachear respuestas de campo.

### 2.6 Formato de error [V]
`{ "code": "...", "message": "...", "fieldErrors": {...}?, "correlationId": "..." }`. Guardar `correlationId` para soporte.

---

## 3. Modelo de datos y reglas transversales

### 3.1 Modalidades (etiquetas visibles) [V]
| Valor técnico | Etiqueta | Quién opera |
|---|---|---|
| `PICKUP` | Recojo en un punto | Encargado `PICKUP` |
| `INTERNAL_DELIVERY` | Entrega en persona | Encargado `INTERNAL_DELIVERY` |
| `DELIVERY` | Delivery | Admin personal / Delivery (empleado) / Operador (cola externa) |

### 3.2 Estados del pedido y quién los mueve [V]
```
CONFIRMED ──Admin──▶ PREPARING ──Admin──▶ READY_FOR_PICKUP (solo PICKUP) ──código──▶ DELIVERED
                          └──Admin──▶ IN_TRANSIT (INTERNAL_DELIVERY y DELIVERY) ──código/complete──▶ DELIVERED
Terminales: DELIVERED, CANCELLED, PICKUP_EXPIRED, LOST
```
| Transición | Quién |
|---|---|
| `CONFIRMED→PREPARING`, `PREPARING→READY_FOR_PICKUP/IN_TRANSIT` | **Admin** (pedido o grupo). En Delivery, también el **empleado Delivery** o el **Operador** sobre sus pedidos (opcional). |
| `READY_FOR_PICKUP→DELIVERED` | Encargado PICKUP con código. |
| `IN_TRANSIT→DELIVERED` (entrega en persona) | Encargado INTERNAL_DELIVERY con código. |
| `IN_TRANSIT→DELIVERED` (Delivery empleado) | Delivery con código. |
| `IN_TRANSIT→DELIVERED` (Delivery externo) | Operador: `complete` (sin código) **o** `handoff` (con código, pago contra entrega). |
| `LOST` | Admin (`mark-lost`) o automático a las 24 h de una incidencia abierta. |
| `PICKUP_EXPIRED` | Automático, 24 h después del fin de la franja (solo pedidos `PAY_FIRST`). |

Cuerpo de avance: `{ "expectedStatus": "...", "targetStatus": "..." }`. Si el estado real ya cambió → `409 ORDER_STATE_CHANGED`: **no reintentar a ciegas**, recargar.

### 3.3 Código de entrega [V]
- 6 caracteres sin 0/O/1/I/L, se compara sin distinguir mayúsculas ni espacios laterales.
- Cualquier código mal formado, vacío o incorrecto responde **la misma** `400 HANDOFF_CODE_INVALID` (no revela si el pedido existe).
- El handoff es idempotente para el mismo usuario; otro usuario recibe `409 HANDOFF_ALREADY_COMPLETED`.
- Recojo: requiere `READY_FOR_PICKUP` y estar dentro de las 24 h posteriores al fin de la franja (`409 HANDOFF_PICKUP_EXPIRED` si no).
- Entrega en persona y Delivery: requiere `IN_TRANSIT`.
- `HANDOFF_CODE_UNAVAILABLE`: pedido antiguo sin código.

### 3.4 Pago contra entrega (`payAtDelivery`) [V]
Solo si `CHECKOUT_PAYMENT_OPTIONS` incluye `ON_DELIVERY` y el cliente lo eligió. Cada tarjeta de campo trae `payAtDelivery { paymentStatus, codeVerified, linkAvailable }`; si falta, el pedido se pagó por adelantado.

- Un pedido pago-contra-entrega **solo pasa a `DELIVERED` con código válido Y pago aprobado, en cualquier orden**.
  - Código primero: respuesta `200 { status: <actual>, completedAt: null, awaitingPayment: true }`. El pedido sigue abierto. Cuando el pago se aprueba, se cierra solo.
  - Pago primero: el código cierra normalmente.
- Cualquier otra vía de cierre sin pago → `409 PAYMENT_REQUIRED`.
- **Enlace/QR de pago:** `GET /api/v1/fulfillment/orders/{orderId}/payment-link` → `{ url }`. Reglas:
  - Encargado: PICKUP e INTERNAL_DELIVERY. Delivery y Operador: DELIVERY. Admin: cualquiera.
  - Solo desde el **inicio de la franja** para el personal (`409 PAYMENT_LINK_NOT_YET`). El Admin siempre.
  - `409` si ya está pagado, no es pago contra entrega o ya no es pagable. `503 PAYMENT_LINK_DISABLED` si no hay secreto configurado.
  - El `url` es una credencial: **no registrarlo en logs ni cachearlo**. Mostrar como QR o compartir por WhatsApp al teléfono del cliente.
- Al terminar la franja el sistema abre incidencias automáticas (sección 3.5).

### 3.5 Incidencias [V]
Una sola entidad, un solo campo de texto. Los tres roles de campo la reportan; se resuelve **fuera de la app** (WhatsApp del Admin con el cliente).

`FulfillmentIncident`: `{ id, orderId, type?, reporterRole?, reporterName?, description, status, recordedAt, deadlineAt?, resolvedAt?, resolvedBy? }`

| `type` | Origen | Ciclo |
|---|---|---|
| `MANUAL` | Personal de campo | `OPEN` → Admin reasigna (`RESOLVED_REASSIGNED`) / da por perdido (`RESOLVED_LOST`) / a las 24 h `AUTO_LOST` / (pago contra entrega) el cliente desiste `RESOLVED_DESISTED`. |
| `SIN_CAMBIO` | Sistema, al terminar la franja si no hubo entrega ni reporte (solo pago contra entrega) | Igual que MANUAL. |
| `IMPAGO` | Sistema, pedido entregado y pago no aprobado al terminar la franja | **Nunca** pasa a `LOST`; no tiene `deadlineAt`. Se cierra con `RESOLVED_PAID` (el cliente pagó) o `RESOLVED_UNCOLLECTIBLE` (Admin). |

Reglas:
- Se puede reportar solo en `CONFIRMED`, `PREPARING`, `READY_FOR_PICKUP`, `IN_TRANSIT` (`409 ORDER_NOT_OPERATIONAL` en otro caso).
- El pedido **no cambia de estado** al reportar. El Admin recibe notificación (campana).
- Al reasignar: el pedido va a otra franja, **pierde su asignación** (desaparece de la lista del campo) y **conserva** `READY_FOR_PICKUP` o `IN_TRANSIT`. En entrega en persona el **Admin decide quién lo entrega** en la nueva franja: el mismo encargado, otro, o lo deja para después (la app abre esa elección justo después de reasignar; N1).
- Cada `POST …/incidents` crea una incidencia nueva → **deshabilitar el botón mientras envía** (no hay idempotency key).
- Los pedidos `LOST` o reasignados dejan de aparecer para Encargado/Delivery/Operador.

### 3.6 Franjas y zonas horarias [V]
- Todo horario está en `America/Lima` (UTC−5, sin horario de verano). `schedule: { slotDate:"yyyy-MM-dd", startTime:"HH:mm", endTime, timezone }`. Mostrar como vienen; calcular "ahora" en Lima, no en la zona del teléfono.
- Franja = fecha + modalidad + universidad + hora (cupo por universidad, desde 2026-10-23). El campo `universityId` puede ser nulo.
- Recojo vence 24 h después del **fin** de la franja.

### 3.7 Paginación [V]
`?page=0&size=20` (máx. 100); respuesta `{items,page,size,totalElements,totalPages}`. Algunas (candidatos, roles) usan `{items,page,hasNext}`. Verificar que `page`/`size` de la respuesta coincidan con lo pedido (la web lo hace).

---

## 4. Flujos por rol

### 4.1 Encargado de RECOJO (`handlerType = PICKUP`)

Pantallas y endpoints [V]:

| Pantalla | Endpoint | Notas |
|---|---|---|
| Arranque | `GET /handler/profile` | `{id, displayName, handlerType}`. Decide la vista. |
| Lista de grupos | `GET /handler/pickup-groups?state=open|pending|all&date=yyyy-MM-dd&page&size` | `open` (por defecto): al menos un pedido `READY_FOR_PICKUP`. `pending`: añade grupos asignados aún en preparación (la web usa `pending`). `all`: todos con pedidos asignados. |
| Grupo abierto | `GET /handler/pickup-groups/{groupKey}` | Pedidos del grupo: `{id, publicCode, status, customerName, incidentCount, delivered, payAtDelivery?}`. |
| Detalle | `GET /handler/orders/{orderId}` | Incluye `pickup {universityName, pickupPointName, locationText}`, `groupKey`. Sirve también pedidos `DELIVERED`/`PICKUP_EXPIRED`. |
| Validar entrega | `POST /handler/orders/{orderId}/handoff` `{code}` | Ver 3.3 y 3.4. |
| Incidencia | `POST /handler/orders/{orderId}/incidents` `{description}` · `GET` mismo path para historial | 201 / `ORDER_NOT_OPERATIONAL`. |
| Enlace de pago | `GET /fulfillment/orders/{orderId}/payment-link` | Solo si `payAtDelivery.linkAvailable`. |
| Próximos trabajos | `GET /handler/upcoming-reminders` | Ver 4.5. |

Reglas propias [V]:
- `groupKey` es **opaca**: viene del servidor, nunca se construye (`pickup-{puntoId}-{yyyyMMdd}-{HHmm}` u `order-{id}` para históricos sin catálogo).
- El Admin asigna **el grupo completo**; los pedidos que se sumen después heredan la asignación.
- Si la lista de pedidos de un grupo aún no tiene `READY_FOR_PICKUP`, la acción de entrega está **bloqueada** (la web muestra "Aún no hay pedidos listos… se habilita cuando el Administrador los marque como listos").
- Un Encargado PICKUP que llama a `GET /handler/orders` recibe `403 HANDLER_TYPE_MISMATCH` (y viceversa).
- No puede haber dos asignaciones con franjas solapadas en lugares distintos (`409 HANDLER_SLOT_BUSY` lo valida el Admin al asignar).

### 4.2 Encargado de ENTREGA EN PERSONA (`handlerType = INTERNAL_DELIVERY` o `null`)

| Pantalla | Endpoint | Notas |
|---|---|---|
| Lista | `GET /handler/orders?page&size` | Solo `INTERNAL_DELIVERY` asignados en `CONFIRMED`, `PREPARING`, `IN_TRANSIT`; orden fecha, hora, id. La web los agrupa por bloque horario. |
| Detalle | `GET /handler/orders/{orderId}` | `recipient {name, phone, place, reference}`. |
| Validar entrega | `POST /handler/orders/{orderId}/handoff` | Requiere `IN_TRANSIT`. |
| Incidencia, enlace de pago, recordatorios | iguales al 4.1 | |

Acciones de contacto (100 % app, sin endpoint) [D]: llamar `tel:` y abrir WhatsApp `https://wa.me/<número>`; un número de 9 dígitos recibe prefijo `51`. WhatsApp **no cambia el estado**.

### 4.3 Delivery (empleado, `DELIVERY`)

| Pantalla | Endpoint | Notas |
|---|---|---|
| Lista | `GET /delivery/orders?page&size` | `{id, publicCode, status, schedule, districtName, addressPreview(60 car.), incidentCount, payAtDelivery?}`. Incluye **todos los estados** mientras siga asignado (no solo operativos) → filtrar en la app los terminales. |
| Detalle | `GET /delivery/orders/{orderId}` | `customerName`, `delivery {districtName, address, reference, phone}`. |
| Avanzar | `POST /delivery/orders/{orderId}/advance` | Solo `CONFIRMED→PREPARING` y `PREPARING→IN_TRANSIT`. `204`. |
| Validar entrega | `POST /delivery/orders/{orderId}/handoff` | Requiere `IN_TRANSIT`. |
| Incidencia | `POST/GET /delivery/orders/{orderId}/incidents` | |
| Enlace de pago, recordatorios | `/fulfillment/orders/{id}/payment-link`, `/delivery/upcoming-reminders` | |

"Copiar dirección": acción local con distrito + dirección + referencia.

### 4.4 Operador (`DELIVERY_COORDINATOR`)

| Pantalla | Endpoint | Notas |
|---|---|---|
| Cola | `GET /operator/orders?page&size` | Misma forma que Delivery. Cola rol-nivel (no es por persona). |
| Detalle | `GET /operator/orders/{orderId}` | Más `provider {name, phone}` si ya se registró. |
| Coordinación | `GET /operator/orders/{orderId}/coordination` | `{provider|null, incidents[]}`. **No existe `GET …/incidents` para el Operador**: las incidencias vienen aquí. |
| Registrar proveedor | `PUT /operator/orders/{orderId}/provider` `{name(≤120), phone(≤40)}` | Dato interno; nunca visible al cliente. `204`. Activo en `CONFIRMED/PREPARING/IN_TRANSIT`. |
| Avanzar | `POST /operator/orders/{orderId}/advance` | Igual que Delivery. |
| Cerrar sin código | `POST /operator/orders/{orderId}/complete` | **Sin cuerpo** (`400` si trae). Requiere `IN_TRANSIT`. En pedidos pago-contra-entrega → `409 HANDOFF_CODE_REQUIRED`. |
| Cerrar con código | `POST /operator/orders/{orderId}/handoff` `{code}` | Solo pago contra entrega. |
| Incidencia | `POST /operator/orders/{orderId}/incidents` | |
| Enlace de pago, recordatorios | iguales | |

Regla: el tercero externo (InDrive, etc.) **no tiene cuenta**. WhatsApp usa `delivery.phone` (teléfono del cliente) y no cambia estado.

### 4.5 Recordatorios (`upcoming-reminders`) [V]
- Endpoints: `/handler|delivery|operator|admin/upcoming-reminders?page&size`. Cada rol ve solo lo suyo.
- Elegibles: `CONFIRMED/PREPARING` y `READY_FOR_PICKUP/IN_TRANSIT` (según modalidad) dentro de las **12 h previas** al inicio de la franja; igualdad con el inicio excluye.
- Cada ítem trae `reachedOffsetsMinutes` ⊂ {720, 180, 60, 30} (umbrales ya alcanzados).
- **No hay push**: el correo agrupado de ~12 h es solo para Admin y Encargado. Delivery y Operador **no reciben correo operativo**. Para avisos en el teléfono hará falta polling o un canal nuevo (decisión D3).

---

## 5. Admin en la app: Pedidos y Asignaciones

### 5.1 Pedidos

| Función | Endpoint |
|---|---|
| Cola | `GET /admin/orders?page&size&code&customer&status&fulfillmentMode&fulfillmentDateFrom&fulfillmentDateTo&refundStatus&incident=open|resolved|none&hasOpenPaymentIncident` (orden `consolidatedAt DESC, id DESC`) |
| Contador de nuevos | `GET /admin/orders/new-count` → `{count}` (pedidos `CONFIRMED`) |
| Detalle | `GET /admin/orders/{orderId}` (ítems, snapshots, notas, historial, incidencias, `paymentTiming`, `stockRestored`, `stockRestoreAvailable`) |
| Nota interna | `POST /admin/orders/{orderId}/notes` `{content 1–2000}` (solo agregar) |
| Avanzar | `POST /admin/orders/{orderId}/advance` (204) |
| Incidencias del pedido | `GET /admin/orders/{orderId}/incidents` |
| Franjas para reprogramar | `GET /admin/orders/{orderId}/reschedule-slots?date=` (sin fecha: próximos 14 días; no aplica la regla de 24 h) |
| Reasignar horario | `POST /admin/orders/{orderId}/incidents/{incidentId}/reassign` `{slotDate,startTime,endTime}` |
| Dar por perdido | `POST …/incidents/{incidentId}/mark-lost` (204) |
| IMPAGO no cobrable | `POST …/incidents/{incidentId}/uncollectible` (204) |
| Cliente desiste | `POST …/incidents/{incidentId}/desist` `{refund: true|false}` (obligatorio) |
| Reponer stock | `POST /admin/orders/{orderId}/restore-stock` (una vez) |
| Reabrir `LOST` | `POST /admin/orders/{orderId}/reopen` `{slotDate,startTime,endTime}` |
| Campana | `GET /admin/incidents/notifications?unread&page&size`, `GET …/unread-count`, `PATCH …/{id}/read` |
| Resumen | `GET /admin/dashboard` (incluye `openFulfillmentIncidents`, `upcomingDeliveries`) |
| Enlace de pago | `GET /fulfillment/orders/{orderId}/payment-link` (Admin, cualquier momento) |

Cada fila de la cola trae `assignmentKind` (`HANDLER`, `DELIVERY_EMPLOYEE`, `OPERATOR_QUEUE`, `null`), `assignedUserId` y `pickupGroupKey`. El resumen **no** incluye teléfono del cliente [V].

Reglas [V]:
- Cancelación por el cliente solo en `CONFIRMED`; no es acción del Admin en este módulo.
- `reassign` quita la asignación y envía correo al cliente. `mark-lost` no reembolsa y no repone stock.
- `desist` y `reopen`/`restore-stock` aplican **solo a pago contra entrega**. Con stock repuesto un `LOST` ya no se puede reabrir (`STOCK_ALREADY_RESTORED`).
- `reassign` exige `RESCHEDULE_SLOT_UNAVAILABLE` si la franja ya no tiene cupo; mostrar y pedir otra.

### 5.2 Asignaciones

**Recojo (por grupo)** [V]
| Función | Endpoint |
|---|---|
| Lista de grupos | `GET /admin/fulfillment/pickup-groups?dateFrom&dateTo&universityId&pickupPointId&assigned&status&page&size` (por defecto hoy + 30 días, máx. 120 días) |
| Detalle del grupo | `GET /admin/fulfillment/pickup-groups/{groupKey}` |
| Candidatos | `GET …/{groupKey}/handler-candidates?page` → `{items:[{id,displayName,available,conflict?}],page,hasNext}` (solo Encargados `PICKUP`; los ocupados vienen con `available=false` y el conflicto) |
| Asignar/reasignar | `PUT …/{groupKey}/handler-assignment` `{handlerId}` → `{groupKey,handlerId,assignedBy,assignedAt,ordersAffected}` |
| Quitar | `DELETE …/{groupKey}/handler-assignment` (204) |
| Avanzar todo el grupo | `POST …/{groupKey}/advance` `{expectedStatus,targetStatus}` → `{advanced:[ids], skipped:[{id,reason}]}` (`CONFIRMED→PREPARING` o `PREPARING→READY_FOR_PICKUP`) |

**Entrega en persona (por pedido)** [V]
| Función | Endpoint |
|---|---|
| Candidatos | `GET /admin/orders/{orderId}/handler-candidates?page` (solo `INTERNAL_DELIVERY`) |
| Leer asignación | `GET /admin/orders/{orderId}/handler-assignment` |
| Asignar | `PUT /admin/orders/{orderId}/handler-assignment` `{handlerId}` |
| Para pedidos de Recojo | siempre `409 ORDER_ASSIGNMENT_USE_GROUP` |

**Delivery (por pedido)** [V]
| Función | Endpoint |
|---|---|
| Leer destino | `GET /admin/orders/{orderId}/delivery-destination` |
| Fijar destino | `PUT …` `{destinationRole:"DELIVERY", employeeId}` o `{destinationRole:"DELIVERY_COORDINATOR", employeeId:null}` |
| Entrega personal del Admin | `DELETE …` (sin destino = el Admin entrega) |

**Personal** [V]
| Función | Endpoint |
|---|---|
| Listar | `GET /admin/users/operational-roles?page&size` (rol y `handlerType`) |
| Crear cuenta | `POST /admin/users/operational-accounts` |
| Cambiar rol/tipo | `PUT /admin/users/operational-roles/{userId}` `{role, handlerType?}` |

Reglas de asignación [V]:
- Candidatos son **consultivos**: la asignación revalida. Errores: `400 HANDLER_TYPE_MISMATCH`, `400 ORDER_HANDLER_INVALID`, `409 HANDLER_SLOT_BUSY` (con `conflicts[]`), `409 ORDER_ASSIGNMENT_STATE_INVALID`, `409 ORDER_ASSIGNMENT_MODE_INVALID`.
- Asignar el mismo encargado de nuevo es no-op.
- Cambiar el rol de alguien con trabajo activo → `409 USER_ROLE_HAS_ACTIVE_ASSIGNMENTS` (reasignar primero). Un Encargado con asignaciones no se puede dar de baja (`AUTH_ACCOUNT_HAS_OPERATIONAL_ASSIGNMENTS`).
- Los Encargados `INTERNAL_DELIVERY` no pueden tener franjas solapadas en lugares distintos [D: supuesto aún sin confirmar].

---

## 6. Catálogo de errores relevantes para la app [V]

| Código | HTTP | Mensaje sugerido / acción |
|---|---|---|
| `AUTH_SESSION_INVALID` | 401 | Sesión vencida → login. |
| `AUTH_FORBIDDEN` | 403 | Sin permiso o CSRF faltante → refrescar CSRF una vez; si persiste, mostrar error. |
| `CAPTCHA_FAILED` / `CAPTCHA_UNAVAILABLE` | 400 / 503 | Verificación del login. |
| (429 rate limit) | 429 | Esperar; mostrar tiempo. |
| `HANDLER_TYPE_MISMATCH` | 403/400 | Vista del tipo equivocado → volver a `profile`. |
| `ORDER_NOT_FOUND` | 404 | Ya no está asignado → quitar de la lista. |
| `GROUP_NOT_FOUND` | 404 | Grupo sin pedidos asignados al usuario. |
| `HANDOFF_CODE_INVALID` | 400 | "Código no válido. Pídalo al cliente (llegó por correo)". |
| `HANDOFF_STATE_INVALID` | 409 | Aún no está en el estado que permite entregar. |
| `HANDOFF_PICKUP_EXPIRED` | 409 | Plazo de recojo de 24 h vencido. |
| `HANDOFF_ALREADY_COMPLETED` | 409 | Ya fue entregado. |
| `HANDOFF_MODE_INVALID` | 409 | Modalidad no corresponde a este rol. |
| `HANDOFF_CODE_UNAVAILABLE` | 409 | Pedido antiguo sin código. |
| `PAYMENT_REQUIRED` | 409 | Pago contra entrega: se cierra al aprobarse el pago. |
| `HANDOFF_CODE_REQUIRED` | 409 | Operador: este pedido se cierra con código. |
| `PAYMENT_LINK_NOT_YET` | 409 | La franja aún no empieza. |
| `PAYMENT_LINK_DISABLED` | 503 | Enlaces no configurados. |
| `INCIDENT_TEXT_INVALID` | 400 | Texto vacío o > 2000. |
| `ORDER_NOT_OPERATIONAL` | 409 | Pedido ya terminó. |
| `ORDER_STATE_CHANGED` | 409 | Estado cambió → recargar. |
| `ORDER_TRANSITION_INVALID` / `ORDER_DELIVERY_TRANSITION_INVALID` | 400 | Par de estados no permitido. |
| `INCIDENT_NOT_OPEN`, `ORDER_ALREADY_TERMINAL`, `RESCHEDULE_SLOT_UNAVAILABLE` | 409 | Admin: acción ya no aplica. |
| `HANDLER_SLOT_BUSY`, `ORDER_ASSIGNMENT_USE_GROUP` | 409 | Admin: asignación bloqueada. |

---

## 7. Datos de visualización útiles

- **Estados (texto)**: Confirmado, En preparación, Listo para recoger, En camino, Entregado, Cancelado, Plazo de recojo vencido, Perdido.
- **Pago**: `pending` Pendiente · `approved` Pagado · `rejected` Rechazado · `cancelled` Cancelado · `unknown` En verificación.
- **Estados de incidencia (texto)**: Abierta · Reasignada por el Administrador · Dada por perdida · Perdida automáticamente (24 h) · Archivada · Cobrada · Dada por no cobrable · El cliente desistió.
- Recomendación: el nombre de rol que ve el usuario es **Encargado / Delivery / Operador**, no los identificadores técnicos.

---

## 8. Matriz de permisos de red (qué puede llamar cada rol) [V]

| Rol | GET | POST/PUT |
|---|---|---|
| `DELIVERY_HANDLER` | todo `/handler/**` | todo `/handler/**` |
| `DELIVERY` | `/delivery/**` | solo `…/orders/{id}/advance`, `…/handoff`, `…/incidents` |
| `DELIVERY_COORDINATOR` | `/operator/**` | `…/advance`, `PUT …/provider`, `…/incidents`, `…/complete`, `…/handoff` |
| `ADMIN` | `/admin/**` | `/admin/**` |
| cualquiera autenticado | `/auth/me`, `/fulfillment/orders/{id}/payment-link` (el servicio valida rol/modalidad) | `/auth/logout` |

Cualquier otra ruta `/delivery/**` o `/operator/**` está **denegada** (`denyAll`). `/public/**` está denegada a los tres roles de campo.

---

## 9. Contradicciones y lagunas detectadas

1. **Docs obsoletos** (tabla de la sección 0): `newdocumentation` v13/v14 y los `AGENTS.md` mantienen foto, "llegué", 4 min, Mercado Pago, "sin capacidad por franja" y etiquetas viejas ("Recojo en universidad", "Entrega interna"). No usarlos.
2. **OpenAPI `BusinessOrderV1.deliveryCode`** dice "siempre nulo para DELIVERY externo"; el código lo genera para todas las modalidades. Pendiente corregir el contrato.
3. **El Admin que entrega Delivery en persona** (sin destino): el contrato no ofrece ningún endpoint de Admin para cerrar a `DELIVERED`. **Decidido (D4, 2026-10-30):** lo cierra con el código de entrega y registrando el pago, como el Delivery interno. Falta la ruta en el backend.
4. **Lista de Delivery/Operador incluye pedidos terminales** (`DELIVERED`, `CANCELLED`, `LOST`…) mientras siga el destino; `LOST`/reasignados desaparecen. La app debe filtrar y ordenar.
5. **Operador sin `GET …/incidents`**; se usa `coordination`. El cliente web ya lo maneja así.
6. **Sin push.** Solo recordatorios por polling y correo (solo Admin/Encargado). Ver D3.
7. **Texto de términos del pago contra entrega** (`v1`) es una propuesta sin aprobar por el negocio; el pago contra entrega sigue apagado por defecto y falta la prueba de punta a punta con Culqi sandbox [D, corte 4].
8. **Regla pendiente del negocio:** cargo creado después de `LOST` y política de 7 días frente a las 24 h de `LOST` [D, corte 3].
9. **Cuándo habilitar la validación de entrega (M5, decidido 2026-10-30):** el cliente recibe el código por correo al comprar, y el rol puede ingresarlo **en cualquier momento** en que el pedido esté en el estado de entrega (`READY_FOR_PICKUP` en recojo; `IN_TRANSIT` en las demás modalidades), sin esperar a que empiece la franja. **Excepción:** mientras el pedido tenga una incidencia abierta no se registra la entrega; la decide el Admin (reasignar, reembolsar o dar por perdido). La app lo aplica así; el backend y la web todavía no lo exigen (ver `PLAN_BACKEND_WEB.md` §10). El Encargado PICKUP sigue dependiendo de que el Admin marque `READY_FOR_PICKUP`.
10. **Horario por universidad** es nuevo (2026-10-23): ventanas antiguas sin universidad sirven a todas. Probar con ambos tipos de datos.
11. **Test fuera de CI:** varias pruebas del backend que usan MySQL real requieren Docker (no se ejecutan sin él); la cobertura real de concurrencia en handoff/asignación no se pudo confirmar aquí.

---

## 10. Decisiones que necesito del negocio/equipo antes de construir

| ID | Pregunta | Mi recomendación |
|---|---|---|
| D1 | **Login y Turnstile**: ¿cómo entra la app a producción con el captcha activo? | **Decidido (2026-10-30): se aprueba la opción B** (el backend exime a un `X-App-Client` con secreto, solo cuentas internas). Requiere el cambio en el backend; mientras el captcha siga apagado (`CAPTCHA_ENABLED=false`, valor por defecto) la app entra sin él. Ver `PLAN_BACKEND_WEB.md` §10. |
| D2 | ¿Alcance Admin dentro de la app? | **Resuelto en la práctica:** la app ya cubre pedidos, asignaciones, incidencias (reasignar, perder, desistir, causa, reembolso), reabrir y reponer stock. |
| D3 | ¿Notificaciones push (FCM) para recordatorios e incidencias? | **Decidido (2026-10-30): sí habrá notificaciones.** Hoy hay avisos locales de pedidos nuevos por rol (revisión cada 15 min, ver `PLAN_APP.md` §9). El aviso inmediato requiere FCM y un endpoint de registro de dispositivo en el backend (`PLAN_BACKEND_WEB.md` §10). |
| D4 | Cierre del Admin que entrega Delivery personalmente (hallazgo 3). | **Decidido (2026-10-30):** el Admin que se asigna a sí mismo un Delivery lo cierra **ingresando el código de entrega y registrando el pago**, con las mismas funciones que el Delivery interno (avanzar, código, cobro con QR o enlace, incidencia). Hoy el backend no le da rutas de cierre al Admin: pendiente en `PLAN_BACKEND_WEB.md` §10; después se agrega la pantalla en la app. |
| D5 | ¿Un mismo teléfono para varios roles (p. ej. Encargado que también es Admin)? Hoy es un rol por cuenta. | Una sola sesión por dispositivo; cerrar sesión para cambiar de rol. |
| D6 | Mínimo de Android y distribución (Play Store o APK directo). | API 26+; APK interno firmado al inicio. |
| D7 | ¿Soporte offline? Hoy toda acción depende del servidor y de CSRF/sesión. | Sin offline en la fase 1; solo mostrar el último listado cacheado en memoria con aviso. |

---

## 11. Orden de construcción propuesto

1. **Base**: proyecto Gradle, Compose + Material 3 con paleta de marca, Retrofit/OkHttp con `CookieJar` y interceptor CSRF, modelo de errores, sesión (`/auth/me`), login según D1.
2. **Encargado entrega en persona** (el flujo más corto: lista → detalle → código → incidencia).
3. **Delivery** (lista, detalle, avanzar, código, incidencia) y **Operador** (cola, coordinación, proveedor, complete/handoff).
4. **Encargado recojo** (perfil → grupos → grupo abierto → entrega por código).
5. **Pago contra entrega** (insignia de pago, enlace/QR, `awaitingPayment`), probando con la opción activada en un entorno de pruebas.
6. **Admin**: cola y detalle de pedidos, avanzar; luego asignaciones (grupos, entrega en persona, destino de Delivery).
7. **Fase 2**: incidencias del Admin, recuperación, campana, push.

Cada pantalla debe cubrir **cargando, error, vacío, validación y feedback** (regla del repositorio), y manejar `401/403/404/409` con los mensajes de la sección 6.
