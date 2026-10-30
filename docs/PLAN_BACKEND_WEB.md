
---

## 9. Observaciones nuevas desde la app (2026-10-30)

Surgieron al implementar `PLAN_APP.md` (rama `fix/app-contingencia-qa` de la app). Todas se verificaron contra el servidor de pruebas o el código del backend.

| ID | Riesgo | Hallazgo | Evidencia | Qué necesita la app |
|---|---|---|---|---|
| N1 | Medio | Reasignar la franja desde una incidencia **borra la asignación del encargado**. El repartidor deja de ver el pedido: `GET /api/v1/handler/orders/12` responde 404. | Pedido `DLM-77YQS2P2` (id 12): tras `POST /admin/orders/12/incidents/1/reassign`, `order_handler_assignments` no tiene fila para el pedido. | **Resuelto (2026-10-30):** es intencional. Reasignar lo hace el Admin y es él quien decide si el pedido queda con el mismo encargado, pasa a otro o queda para después. La app ya abre esa elección justo después de reasignar un envío en persona. Sin cambio de backend. |
| N2 | Bajo | La personalización de un **producto suelto** no se puede mostrar: el `commercialSnapshot` copia `configuration` solo con ids (`group_<id>` → `option_<id>`). | `SellableUnitCustomizationResolver.java:147,153`; `CommercialSnapshot.java:57,68`. Los componentes de packs y combos sí traen `summary` legible (`CartService.java:392-397`). | Agregar un `summary` legible ("Grupo: opción; …") también a nivel de ítem en el snapshot, como ya existe para los componentes. |
| N3 | Bajo | El snapshot de precios (`pricingSnapshot.items`) no trae el nombre del producto. | `admin_order_12`: las líneas solo tienen `quantity`, `subtotal` y `cartItemId`. | Ninguno urgente: la app ya toma las líneas de `items` del pedido. Si se quiere unificar, incluir `lineNumber` o el nombre en `pricingSnapshot.items`. |
| N4 | — | B7 (teléfono del proveedor) ya tiene la regla del lado de la app: `^\+[1-9]\d{7,14}$` sobre el valor recortado, enviando el valor recortado. | `app/.../ui/common/FormRules.kt`. | Usar la **misma** regla en `PUT /api/v1/operator/orders/{id}/provider`, para que la app y el backend no diverjan. |
| N5 | — | B8 (foto de incidencias): la app ya valida el texto (vacío, 2000 caracteres, conserva el borrador) y espera el contrato de la foto. | `IncidentDtos.ReportRequest` solo acepta `description`. | Definir el endpoint de carga y la consulta de la evidencia (ver §5); la app implementa después la selección, la previsualización y el reintento. |
| N6 | — | El polling de las colas está implementado en la app pero **apagado en release** hasta cerrar M3. | `QUEUE_POLLING_ENABLED=false` fijo en release (`app/build.gradle.kts`). | Avisar cuando M3 esté verificado (IP efectiva y límites aislados) para habilitarlo. |
| N7 | — | Al reintentar el registro con un `termsVersion` incorrecto, el backend responde `TERMS_REQUIRED` antes de detectar que el email ya existe. Ya figura como B4; se agrega este caso. | Registro de `cliente@dleche.test` con `v2` después de crearlo con `v1`. | Ver B4. |

**Workaround activo en el servidor de pruebas (por A3):** las ventanas de entrega en persona están cargadas "para todas las universidades" (`universityId: null`). Al corregir A3 hay que restituirlas por universidad siguiendo §3.

---

## 10. Decisiones del negocio y lo que piden al backend (2026-10-30)

La app es una extensión de la página: mismas reglas, para cada rol de campo. Estas decisiones cierran preguntas abiertas de `MAPA_DE_PROCESOS.md` y `PLAN_CONTINGENCIA_QA.md`.

| ID | Decisión | Qué necesita el backend (o la web) | Estado en la app |
|---|---|---|---|
| M5 | El código de entrega (que el cliente recibe por correo al comprar) se puede ingresar **en cualquier momento** en que el pedido esté en estado de entrega, **salvo con una incidencia abierta**. | `OrderHandoffService` no revisa hoy las incidencias abiertas, y la web (`canConfirm`) tampoco. Exigirlo en el servidor y en la web para que las dos superficies coincidan. | Hecho: el detalle del pedido y la tarjeta de la lista no permiten registrar la entrega con una incidencia abierta; las demás reglas no cambian. |
| N1 | Reasignar lo hace el Admin y él decide si deja al mismo encargado, elige otro o lo deja para después. | Nada: es el comportamiento actual. | Hecho: tras reasignar un envío en persona se abre la elección de encargado. |
| D1 | Se aprueba la opción B: el backend exime del captcha a la app con un `X-App-Client` y un secreto, solo para cuentas internas. | Implementar la exención en `/auth/login/local`. Mientras `CAPTCHA_ENABLED=false` (valor por defecto) no hace falta. | Pendiente: enviar la cabecera cuando el backend la defina. |
| D3 | Habrá notificaciones. | Endpoint de registro de dispositivo (token FCM por usuario y rol) y envío al asignar un pedido o abrirse una incidencia. | Hecho en versión local (revisión cada 15 min). La app pasará a FCM cuando exista el endpoint. |
| D4 | El Admin que se asigna a sí mismo un Delivery lo cierra **ingresando el código y registrando el pago**, con las mismas funciones que el Delivery interno. | Hoy el Admin no tiene rutas de entrega (`/delivery/**` y `/operator/**` están restringidas a su rol). Dar al Admin avance, código de entrega, enlace de pago e incidencias sobre los pedidos que se entrega él mismo. | Pendiente hasta que existan las rutas: se reutilizará la pantalla del Delivery interno. |
