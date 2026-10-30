
---

## 9. Observaciones nuevas desde la app (2026-10-30)

Surgieron al implementar `PLAN_APP.md` (rama `fix/app-contingencia-qa` de la app). Todas se verificaron contra el servidor de pruebas o el código del backend.

| ID | Riesgo | Hallazgo | Evidencia | Qué necesita la app |
|---|---|---|---|---|
| N1 | Medio | Reasignar la franja desde una incidencia **borra la asignación del encargado**. El repartidor deja de ver el pedido: `GET /api/v1/handler/orders/12` responde 404. | Pedido `DLM-77YQS2P2` (id 12): tras `POST /admin/orders/12/incidents/1/reassign`, `order_handler_assignments` no tiene fila para el pedido. | Confirmar si es intencional. Si lo es, el admin debería ver un aviso de "Pedido sin encargado" después de reasignar; si no, conservar la asignación. |
| N2 | Bajo | La personalización de un **producto suelto** no se puede mostrar: el `commercialSnapshot` copia `configuration` solo con ids (`group_<id>` → `option_<id>`). | `SellableUnitCustomizationResolver.java:147,153`; `CommercialSnapshot.java:57,68`. Los componentes de packs y combos sí traen `summary` legible (`CartService.java:392-397`). | Agregar un `summary` legible ("Grupo: opción; …") también a nivel de ítem en el snapshot, como ya existe para los componentes. |
| N3 | Bajo | El snapshot de precios (`pricingSnapshot.items`) no trae el nombre del producto. | `admin_order_12`: las líneas solo tienen `quantity`, `subtotal` y `cartItemId`. | Ninguno urgente: la app ya toma las líneas de `items` del pedido. Si se quiere unificar, incluir `lineNumber` o el nombre en `pricingSnapshot.items`. |
| N4 | — | B7 (teléfono del proveedor) ya tiene la regla del lado de la app: `^\+[1-9]\d{7,14}$` sobre el valor recortado, enviando el valor recortado. | `app/.../ui/common/FormRules.kt`. | Usar la **misma** regla en `PUT /api/v1/operator/orders/{id}/provider`, para que la app y el backend no diverjan. |
| N5 | — | B8 (foto de incidencias): la app ya valida el texto (vacío, 2000 caracteres, conserva el borrador) y espera el contrato de la foto. | `IncidentDtos.ReportRequest` solo acepta `description`. | Definir el endpoint de carga y la consulta de la evidencia (ver §5); la app implementa después la selección, la previsualización y el reintento. |
| N6 | — | El polling de las colas está implementado en la app pero **apagado en release** hasta cerrar M3. | `QUEUE_POLLING_ENABLED=false` fijo en release (`app/build.gradle.kts`). | Avisar cuando M3 esté verificado (IP efectiva y límites aislados) para habilitarlo. |
| N7 | — | Al reintentar el registro con un `termsVersion` incorrecto, el backend responde `TERMS_REQUIRED` antes de detectar que el email ya existe. Ya figura como B4; se agrega este caso. | Registro de `cliente@dleche.test` con `v2` después de crearlo con `v1`. | Ver B4. |

**Workaround activo en el servidor de pruebas (por A3):** las ventanas de entrega en persona están cargadas "para todas las universidades" (`universityId: null`). Al corregir A3 hay que restituirlas por universidad siguiendo §3.
