# Plan de corrección — App Android de personal

- **Origen:** `QA_HALLAZGOS.md` (QA del 2026-10-29) y `PLAN_CONTINGENCIA_QA.md` (revisión con especialista).
- **Alcance:** solo la app `dlecheymielapp`. El backend y la web están en `PLAN_BACKEND_WEB.md`.
- **Estado del código:** la carpeta local no tiene Git. Antes de empezar, crear un repositorio o una línea base reproducible (ver §6).

## 1. Resumen

| ID | Riesgo | Hallazgo | Entrega | Depende de backend |
|---|---|---|---|---|
| A1 | Alto | Deslizar para actualizar no funciona con la lista vacía | 1 | No |
| A2 | Alto | Las listas no se recargan al volver ni al cambiar de pestaña | 1 | Sí, el polling espera a M3 |
| M2 | Medio | Doble avance de estado sin confirmación | 1 | Opcional (ver §3) |
| M1 | Medio | La cookie de sesión se transfiere entre dispositivos | 2 | No |
| B1 | Bajo | Aviso de sesión vencida | 2 (solo verificar) | No |
| B2 | Bajo | "Cerrar sesión" solo en la pestaña Pedidos | 2 | No |
| B7 | Bajo | Teléfono del proveedor sin validar | 2 | Sí (regla común) |
| B8 | Bajo | Incidencias: sin feedback al enviar vacía y sin foto | 3 | Sí, la foto requiere API nueva |
| B9 | Bajo | Detalle de pedido y textos | 3 | No (los datos ya llegan) |
| B5 | Bajo | Release sin ofuscación | 3 | No |

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

- cola vacía y con datos;
- asignación y reasignación;
- volver del fondo y cambiar de pestaña;
- pérdida de conexión;
- sesión revocada;
- acceso a pedidos fuera de su alcance.

### Flujos todavía sin probar en la app

| Flujo | Qué verificar |
|---|---|
| DELIVERY con pedidos | Asignar, ver, avanzar, reportar incidencia y completar la entrega, incluida una reasignación concurrente. |
| Pago contra entrega | Código correcto sin pago, pago aprobado con el código ya validado, respuesta de pago incierta. |
| Packs, combos y personalización | Que el encargado vea lo necesario para preparar. |
| Reembolsos | Que se muestren los estados de reembolso. |

### Capas de prueba

- Unitarias de reglas y estados de los ViewModels.
- UI (Compose) para el gesto de actualizar, la navegación y el doble toque.
- APK release en un dispositivo real.

---

## 7. Despliegue y reversión

**Preparación**
- Inicializar Git en `dlecheymielapp` (no tiene metadatos) o fijar una copia de referencia del código probado.
- Identificar el APK exacto probado (`versionCode`, firma y URL del API).

**Orden**
1. APK piloto con la Entrega 1, **sin polling** hasta que el backend cierre M3.
2. Activar el polling después de M3.
3. Entregas 2 y 3.
4. Ampliar a todo el personal después de un turno completo sin problemas.

**Reversión**
- Tener lista una versión de recuperación firmada con la misma clave y un `versionCode` mayor. No depender de desinstalar, que borra datos.

**Disparadores de reversión:** pedidos invisibles, avances no autorizados o pérdida de acceso generalizada.

## 8. Criterios de cierre

- A1, A2 y M2 pasan su aceptación en el APK candidato.
- M1 pasa la prueba de restauración y migración.
- B1 queda verificado en el APK.
- Un turno piloto termina sin pedidos invisibles ni avances accidentales.
