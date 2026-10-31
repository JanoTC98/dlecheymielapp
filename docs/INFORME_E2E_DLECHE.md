# Informe E2E — D'Leche y Miel (app Android del personal + backend)

Fecha: 2026-10-31. Dispositivo: Xiaomi 23117RA68G (celular físico por USB). Build: `main` @ `ed581dc` + 1 cambio local sin commitear (ver APP-01).
Backend probado: `https://dleche.famespinoza.net.pe` (servidor `100.122.225.43`), recién actualizado a `develop` (Flyway V77).
Pagos: solo tarjetas de prueba oficiales de Culqi. El backend corre con `sk_test`, `CULQI_LIVE_MODE=false`; el frontend usa `pk_test`. No se vio ningún id `live` ni se tocó ninguna cuenta bancaria real.

## Cuentas creadas (datos de prueba en el servidor)
| Rol | Correo | Contraseña |
|---|---|---|
| Administrador | admin@dleche.com | Admin123! |
| Delivery (DELIVERY) | delivery@dleche.com | Delivery123! |
| Encargado de recojo (DELIVERY_HANDLER, PICKUP) | encargado@dleche.com | Encargado123! |
| Operador (DELIVERY_COORDINATOR), extra | operador@dleche.com | Operador123! |
| Clientes de prueba | qa.client1..5@dleche.com | Client123! |

Los pedidos creados están en `…/scratchpad/orders/ORDERS.md` (15 pedidos, 10 activos y 5 cancelados a propósito).

## Resumen
- 1 bloqueante (crash tras login), 0 críticos de seguridad, 3 medios, varios bajos.
- Funcional: login, colas, asignaciones, avance de estados, notas, incidencias, reasignación de franja, cierre por código (prepago), pago contra entrega por link, sin red, rotación: funcionan.

## Hallazgos por severidad

### Bloqueante
- **APP-01 — La app se cae justo después de iniciar sesión (todos los roles).**
  `SecurityException: android.permission.ACCESS_NETWORK_STATE required for jobs with a connectivity constraint` en `OrderAlertScheduler.schedule` (`OrderAlertService.kt:87`), llamado desde `MainActivity.kt:77`.
  Causa: `setRequiredNetworkType(NETWORK_TYPE_ANY)` exige ese permiso y el `AndroidManifest.xml` no lo declara.
  Arreglo: añadir `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />`. **Está aplicado en el árbol de trabajo, sin commitear**, solo para poder seguir probando. Decide si lo commiteás.

### Medios
- **SEC-01 — Validación del código de entrega sin límite visible de intentos.** 7 códigos erróneos seguidos sobre un mismo pedido (operador) no produjeron bloqueo (el login sí limita). Falta verificar el límite por pedido en el servidor; el código tiene 6 caracteres.
- **F-01 (API) — El paso 3DS aceptó datos inventados.** `authentication3DS` con `cavv:"fake"` y la tarjeta de desafío 4456 5300 0000 1096: pago aprobado y pedido creado. Probablemente es permisividad del sandbox de Culqi; hay que confirmar que en producción lo rechace.
- **F-02 (API) — Métodos HTTP o `Content-Type` no soportados devuelven 500 `INTERNAL_ERROR`** en vez de 405/415 (DELETE `/client/orders/13`, PUT `/client/cart`, GET `/auth/profile`, body `text/plain`).
- **APP-05 — El contador "Para entregar ahora" cuenta pedidos que no corresponden.** Delivery mostró 1 "para entregar ahora" con un pedido de la franja del miércoles 4 nov (hoy es sábado 31). El operador también contó uno cuya franja de las 18:00 aún no empezaba.

### Bajos / UX
- **APP-02** — Líneas del pedido como "2 × Rol de canela · S/ 12.00": S/ 12.00 es el subtotal de la línea, pero se lee como precio unitario.
- **APP-03** — En el detalle del pedido (admin) la tarjeta "Encargado de entrega" / "Destino del delivery" no muestra el asignado actual; solo se ve dentro del diálogo ("Actual: …") o en la lista ("Con encargado"). El historial tampoco registra la asignación.
- **APP-04 / APP-07** — Guardar una nota vacía no da mensaje; guardar proveedor no confirma; el teléfono del proveedor guardó/contiene `abc` (sin validar).
- **APP-06 / APP-08** — Tras validar el código, el formulario de código sigue visible; en el encargado queda un error rojo viejo ("El código no es válido") después de registrar una incidencia.
- **APP-09** — Dirección con distrito duplicado en la vista de delivery: "Av. Los Alisos 123, Los Olivos, Los Olivos".
- **APP-10** — El cobro con QR/link se bloquea hasta que empiece la franja (comportamiento coherente), pero el código de entrega sí se acepta días antes de la franja. Conviene decidir si es intencional.
- **SEC-03** — La cookie de sesión se guarda en `SharedPreferences` en claro. Mitigado con `allowBackup=false` y exclusiones de extracción; expuesta solo en dispositivos con root.
- **SEC-04** — `HttpLoggingInterceptor` nivel BASIC en `NetworkModule.kt:36`; confirmar que solo corre en debug.
- **F-03** — La cookie `DLECHE_SESSION` no tiene `Secure`; `XSRF-TOKEN` no tiene `SameSite`.
- **F-04** — Los 401/403 se sirven en ISO-8859-1 ("Sesi?n") y el resto en UTF-8.
- **F-05** — Desajustes con `openapi.yaml`: el carrito exige `productId`; `checkout-config` devuelve `paperBagFee` no documentado; `GET …/payment` devuelve 204 y no 404; sin stock en contra entrega responde `CHECKOUT_REVALIDATION_REQUIRED` y no `STOCK_UNAVAILABLE`.
- **F-06** — Se ignoran campos extra en `checkout-attempts` aunque el contrato dice `additionalProperties:false`; el total siguió siendo el del servidor.
- **F-07** — Texto libre (nombre, dirección, evento) se guarda sin sanitizar. En la app se vio escapado correctamente (nota con `<script>` se muestra como texto). Falta revisar los paneles web.
- **F-08** — Un pago rechazado no muestra el motivo al cliente.
- **F-09** — La respuesta del pedido expone `providerOrderId` (id del cargo de Culqi).
- **F-10** — El límite de login es por IP (10 cada 5 min) y `checkout-attempts` 20 cada 10 min: una red compartida (universidad) lo agota rápido. 429 sin cabecera `Retry-After`. No hay captcha activo.
- **Nota CSRF** — El token CSRF rota tras cada escritura: reutilizar el anterior da 403 `AUTH_FORBIDDEN`. Los clientes deben releerlo.

## Verificado y correcto
- **Pagos:** 5 rechazos de Culqi (robada, fondos insuficientes, contactar emisor, CVV incorrecto, fraudulenta) dejan el pago `rejected` sin crear pedido. Pago contra entrega por link `/pagar/…` con la tarjeta 4111 1111 1111 1111: "Pago recibido" (S/ 19.00, coincide con el pedido). Cancelar un pedido pagado reembolsa en segundos y restaura stock.
- **Seguridad API:** IDOR devuelve 404; con sesión CLIENT las rutas admin/handler/delivery/operator dan 403 (401 sin sesión); CSRF incorrecto da 403; el `role` enviado por mass assignment se ignora; totales calculados por el servidor; idempotencia correcta; cabeceras y CORS bien; login sin enumeración de usuarios y con 429.
- **App:** admin (lista, filtros, búsqueda con Enter, detalle, QR de pago, notas, asignación de encargado y de destino, avance de estados, grupos de recojo, incidencias, reasignar franja), operador (coordinación, proveedor, validación de código), encargado (grupo de recojo, incidencia con pedido retenido), delivery (cierre por código: pedido pasó a Entregado). Sesión persiste tras reiniciar; rotación sin crash; sin red muestra "No hay conexión… Datos desactualizados" y se recupera; login limita tras 5 intentos fallidos. Manifest: `usesCleartextTraffic=false`, `allowBackup=false`, solo `MainActivity` exportada, R8 en release.

## No probado y por qué
- **Combos, packs y promociones:** el catálogo del servidor no tiene ninguno y crear catálogo no estaba autorizado. Si lo autorizás, creo datos QA y pruebo esos pedidos.
- **3DS sin desafío / timeout / error de autenticación:** requieren la librería 3DS de Culqi en navegador.
- **Pago contra entrega del operador/encargado desde la app:** el cobro se habilita al empezar la franja (5 nov 15:00 para el pedido 17); se probó el mismo endpoint como admin.
- **Reembolsos desde incidencias, "desistió", "dar por perdido"** y notificaciones push (polling desactivado hasta M3).
- **Frontend web de administración y tienda:** solo se probó la página de pago por link.
- **Entregas pendientes de cierre:** pedido 17 (código validado, esperando el pago en su franja).
