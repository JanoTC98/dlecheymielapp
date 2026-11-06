# Pendientes del backend

Origen: regresión completa de la app Android del personal, 6 de noviembre de 2026, contra `https://dleche.famespinoza.net.pe` (Culqi en `sk_test`). La app ya absorbe lo que le corresponde; esto es lo que solo puede resolverse en el servidor.

## 1. Un pedido puede tener varias incidencias abiertas (prioridad media)

**Qué pasa.** `POST /api/v1/{operator|handler|delivery}/orders/{id}/incidents` sobre un pedido que ya tiene una incidencia `OPEN` crea **otra** incidencia `OPEN`. El pedido queda con dos abiertas, y el administrador debe resolverlas por separado.

**Cómo reproducir.** Pedido `DLM-9FM6D93E`: tiene una incidencia del sistema abierta ("el pago no se confirmó al terminar la franja"). Un operador reporta otra desde la app → el detalle lista dos incidencias "Abierta".

**Qué esperamos.** Decidir una de dos:
- Un pedido nunca tiene dos abiertas: el segundo reporte se agrega como **nota** de la abierta (`notes[]` con autor, texto y fecha) y la respuesta lo indica.
- O se permiten varias, pero entonces la lista de incidencias del administrador debe agruparlas por pedido.

**Impacto en la app.** La app hoy solo advierte antes de reportar ("este pedido ya tiene una incidencia abierta"). Si se elige la opción de notas, la app puede mostrarlas; un parche ya probado está guardado fuera del repo (`notas_incidencia_sin_commit.patch`) y supone el campo `notes` en `FieldIncident`/`AdminIncident`.

## 2. El conteo de incidencias de la lista no distingue abiertas de resueltas

`incidentCount` en `HandlerOrderWork`/`PickupGroupOrderWork` se muestra en la tarjeta como "N incidencias registradas" y bloquea la entrega ("mientras esté abierta no se registra la entrega"). La app no puede saber si las N están abiertas o resueltas.

**Pedido.** Exponer `openIncidentCount` (o documentar que `incidentCount` ya cuenta solo abiertas). Con eso la tarjeta puede decir "Incidencia abierta" sin abrir el detalle.

## 3. Política del código de entrega antes de la franja (APP-10)

El servidor acepta el código del cliente días antes de la franja, pero bloquea el cobro por link hasta que la franja empieza. Hay que decidir si es intencional y, si no, rechazar la validación anticipada con un código de error estable.

## 4. Límite de intentos del código de entrega por pedido (SEC-01)

7 códigos erróneos seguidos sobre un mismo pedido no produjeron bloqueo (el login sí limita). El código tiene 6 caracteres. Falta un límite por pedido y por actor.

## 5. Límite de intentos de login: documentar y alinear

Observado hoy: 4 intentos fallidos devuelven 401 y el **quinto** devuelve `429 RATE_LIMITED` con `Retry-After: 52`. Las notas de la prueba del 31 de octubre hablan de 10 por 5 minutos. Confirmar el valor real y documentarlo; la app muestra "Demasiados intentos. Espera 1 min…" y asume que el bloqueo es corto.

## 6. Codificación de errores 401, 403 y 429

Siguen saliendo en ISO-8859-1 mientras el resto es UTF-8 (`Sesi�n no válida`, `Int�ntalo de nuevo…`). Un cliente que lea UTF-8 muestra caracteres rotos. Debe ser UTF-8 en todas las respuestas, con `Content-Type: application/json; charset=utf-8`.

## 7. Errores 500 donde corresponde 4xx

Métodos HTTP o `Content-Type` no soportados responden `500 INTERNAL_ERROR` en vez de 405/415 (`DELETE /client/orders/13`, `PUT /client/cart`, `GET /auth/profile`, cuerpo `text/plain`). También `GET /admin/catalog/products/{id}` (no documentado) responde 500.

## 8. Autenticación y cookies

- `DLECHE_SESSION` sin atributo `Secure`; `XSRF-TOKEN` sin `SameSite`.
- El token CSRF rota tras cada escritura (reutilizar el anterior da 403); documentarlo en el contrato.
- `POST /api/v1/auth/login` responde 401 con cuerpo mal codificado en vez de 404 (solo existe `/auth/login/local`).
- Cloudflare bloquea el `User-Agent` por defecto de `Python-urllib` (error 1010): los scripts de QA deben enviar uno propio.

## 9. Pagos (Culqi)

- El paso 3DS aceptó datos inventados (`authentication3DS` con `cavv:"fake"` y la tarjeta de desafío 4456 5300 0000 1096): pago aprobado y pedido creado. Probablemente es permisividad del sandbox; confirmar que en producción se valida.
- Un pago rechazado no muestra el motivo al cliente.
- `GET …/payment` devuelve 204 y no 404; el carrito exige `productId` para ítems `PRODUCT` aunque el contrato no lo dice; `checkout-config` devuelve `paperBagFee` no documentado; se ignoran campos extra en `checkout-attempts` pese a `additionalProperties:false`.

## 10. Avisos de pedido nuevo

La app arma el aviso "Nuevo pedido" con `schedule.slotDate` y `schedule.timeRange`. Si algún día hay notificaciones push del servidor, que usen el mismo formato legible ("Viernes 6 de noviembre · 10:00 – 11:00").

---
Los hallazgos 3 a 9 ya estaban en el informe del 31 de octubre (`ISSUES_BACKEND_WEB.md`); aquí se consolidan. De ellos, **hoy solo reproduje el 6** (respuestas 401 y 429 en ISO-8859-1) y el 5 (el 429 llegó al quinto intento). Los demás no se repitieron en esta pasada.
