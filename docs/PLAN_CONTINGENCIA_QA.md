## Conclusión

**Recomiendo frenar la distribución general hasta resolver A1, A2, A3 y M2, y verificar el alcance real de M3.** Son los puntos que pueden impedir recibir pedidos, bloquear ventas o provocar avances accidentales.

Revisé el código Android local, el backend por SSH en `/srv/projects/apis/dleche-y-miel-backend` y el frontend en `/srv/projects/websites/dleche-y-miel-frontend`. La revisión fue de solo lectura: **no ejecuté correcciones, despliegues ni pruebas que modifiquen datos**. Los 69 tests aprobados siguen siendo evidencia del informe QA, no una ejecución nueva.

# Plan de contingencia

## 1. Ajustes necesarios al diagnóstico

El informe es una buena base, pero el código actual obliga a precisar estos puntos:

| Punto | Resultado de la revisión |
|---|---|
| **A1 y A2 — actualización de listas** | Confirmados. Los estados vacíos carecen de desplazamiento y no hay actualización por reanudación. Admin sí refresca algunas listas al regresar mediante la navegación interna del detalle; cambiar de pestaña no lo hace. |
| **A3 — universidad en entrega interna** | Confirmado. `CheckoutAttemptService.java:203` llama a `withPickupPoint(null)`, y `CheckoutAttemptDtos.java:60–64` elimina `universityId`. La validación posterior rechaza la franja. |
| **M2 — doble avance** | La app bloquea mientras la petición está en curso, pero permite otra transición tras recibir la respuesta. El backend ya recibe `expectedStatus` y `targetStatus`: dos transiciones consecutivas válidas no quedan impedidas por ese control. |
| **M3 — bloqueo de cuentas** | Confirmado: `LocalAuthService.java:74–88` bloquea por email tras cinco fallos durante una ventana de 15 minutos. **También existe** un límite independiente por IP de 10 solicitudes de login cada cinco minutos; probar cinco fallos no alcanza para observarlo. |
| **M3 — IP compartida** | Riesgo respaldado por la configuración: el backend confía en `X-Forwarded-For` y toma su última entrada; Caddy está detrás del túnel `172.30.0.2`. Falta observar la IP efectiva recibida por la aplicación para cerrar la hipótesis. |
| **M4 — CSRF después de un 400** | La eliminación de la cookie está reportada por QA, pero su causa no quedó demostrada estáticamente. Tanto Android como la web intentan recuperar el token cuando falta. Hay que reproducir la secuencia completa, incluyendo concurrencia y cookies. |
| **B1 — aviso de sesión vencida** | **Ya está implementado en el código local:** `AuthRepository.kt:59–63`, `LoginViewModel.kt:32–36` y `LoginScreen.kt:118`. Corresponde verificar el APK, no reimplementar el flujo. |
| **B8 — fotos de incidencias** | La migración V49 existe, pero **la API actual no ofrece ese soporte**: `IncidentDtos.ReportRequest` solo admite `description` y rechaza campos adicionales. Requiere backend y app. |
| **B9 y cobertura pendiente** | El backend entrega `pricingSnapshot`, `refundStatus` y `commercialSnapshot`; los modelos Android omiten esos datos. Hay una brecha de consumo y presentación. |
| **B10 — desbordamiento** | Hay una causa concreta en CSS: `.secure-note` tiene `white-space: nowrap`, y se reutiliza en ambos textos largos. También confirmé la fecha sin formatear y los contactos de relleno en el footer global. |

**Otra precisión importante:** “perdido automático a las 24 horas” no es una regla universal. El backend considera la causa de la incidencia y si existe un pago aprobado. Una incidencia atribuible al negocio con dinero cobrado se escala al administrador, en lugar de dar automáticamente el pedido por perdido.

---

## 2. Contención inmediata

Estas medidas permiten sostener una operación controlada mientras se preparan las correcciones.

| Riesgo | Medida temporal | Responsable |
|---|---|---|
| **Pedidos invisibles — A1/A2** | El administrador comunica cada asignación por el canal operativo y exige confirmación de recepción. Ante una cola vacía desactualizada, reiniciar completamente la app y comprobar el pedido. Conciliar asignaciones pendientes al inicio y cierre del turno. | Operaciones |
| **Checkout bloqueado — A3** | Mantener las ventanas compartidas únicamente en el entorno de pruebas donde ya se aplicó el workaround. En operación real, usarlas solo con control manual de capacidad total y por universidad; si eso no es viable, suspender temporalmente esa modalidad y ofrecer las disponibles. | Operaciones + backend |
| **Avances accidentales — M2** | Indicar un solo toque y esperar la confirmación visual. Si ocurre un avance incorrecto, registrar pedido, estado anterior, hora y notificaciones emitidas; corregir mediante el flujo administrativo permitido. | Operaciones |
| **Bloqueo de acceso — M3** | Detener reintentos repetidos, revisar los 429 y respetar la ventana de espera. Escalar bloqueos de cuentas operativas. No desactivar globalmente los límites para recuperar acceso. | Backend + infraestructura |
| **Error CSRF — M4** | Conservar los datos del formulario y recuperar el contexto de sesión antes de reenviar. En cobros o acciones con resultado incierto, consultar el estado primero. | Web + soporte |
| **Entrega anticipada — M5** | Hasta definir la política, instruir al personal para validar el código al realizar la entrega física. Revisar los casos anticipados identificados por QA. | Negocio + operaciones |
| **Incidencias sin foto — B8** | Registrar la incidencia textual y vincular cualquier evidencia adicional mediante un canal interno autorizado y el identificador del pedido. | Operaciones |
| **Teléfono externo inválido — B7** | Verificar manualmente el contacto del proveedor antes de despachar. | Coordinación |

**Salida de la contingencia:** retirar cada procedimiento manual solo cuando su corrección esté desplegada y verificada por el rol afectado.

---

## 3. Primera entrega: continuidad operativa

### A1 + A2 — actualización fiable de pedidos

**Implementación**

- Hacer desplazables los estados vacíos y revisar también los estados de error dentro de `PullToRefreshBox`.
- Ofrecer una acción explícita **Actualizar**, además del gesto.
- Refrescar al volver al primer plano y al activar una pestaña.
- Unificar el comportamiento de regreso desde detalle, incluido el botón Atrás del sistema.
- Para las colas operativas, incorporar actualización periódica **solo mientras la pantalla esté activa**. Propuesta inicial: cada 60 segundos, después de validar M3 y la carga.
- Evitar solicitudes superpuestas; conservar filtros, posición y datos anteriores si falla una actualización.
- Mostrar última actualización y aviso de datos desactualizados.
- Cancelar tareas al salir de la sesión y evitar que una respuesta tardía de un usuario afecte a otro.

**Archivos principales:** `HandlerOrdersScreen.kt`, `PickupScreens.kt`, pantallas de listas admin, `AdminHost.kt` y ViewModels de listas.

**Aceptación**

1. Una cola vacía recibe una asignación y la muestra al actualizar, sin reiniciar.
2. Volver del fondo y activar una pestaña actualiza los datos.
3. Con la pantalla activa y conectividad normal, una asignación aparece en un ciclo de actualización más el tiempo de respuesta.
4. No hay polling en segundo plano ni peticiones duplicadas por recomposición.
5. Una caída de red no convierte una lista con datos en una lista vacía engañosa.

### A3 — checkout por universidad

**Implementación**

- Separar la normalización de recojo y entrega interna para conservar la universidad explícitamente.
- Mantener la validación de universidad activa y de correspondencia con la franja.
- Evitar cambiar indiscriminadamente `withPickupPoint`, porque también se utiliza en otras modalidades.
- Verificar snapshots, tarifa y capacidad por universidad.

**Compatibilidad que debe resolverse antes del despliegue:** la huella de idempotencia incluye la universidad cuando está presente. Al dejar de eliminarla, un reintento de un checkout creado con el comportamiento anterior puede producir un conflicto de payload. Se necesita una estrategia acotada para intentos anteriores, sin ignorar diferencias de universidad en los nuevos.

**Aceptación**

- Universidad y franja coincidentes: creación correcta.
- Universidad distinta: 409 específico.
- Universidad inactiva o datos inválidos: rechazo controlado.
- Franjas compartidas: siguen funcionando según contrato.
- Reintentos: no duplican intentos, reservas ni cobros.
- `PICKUP` y `DELIVERY`: sin regresiones.

**Restitución de horarios**

1. Inventariar ventanas compartidas, pedidos, intentos pendientes y capacidad comprometida.
2. Crear las ventanas universitarias y definir la transición de oferta.
3. Evitar publicar capacidad duplicada durante la coexistencia.
4. Preservar los snapshots y compromisos de pedidos existentes.
5. Retirar el workaround solo después de comprobar disponibilidad y creación de pedidos.

### M2 — una intención, una transición

**Implementación**

- Mantener el bloqueo por pedido hasta terminar la actualización posterior a la operación.
- Pedir confirmación explícita para **En camino** y transiciones terminales; identificar el pedido y el destino del cambio.
- Aplicar el criterio a acciones individuales y grupales.
- Conservar las precondiciones de estado existentes en el backend.
- Ante timeout o conflicto, consultar el estado antes de permitir repetir.

**No usar un retardo de unos milisegundos como única protección:** depende de la velocidad del dispositivo y de la red.

**Aceptación:** doble toque antes, durante y después de una respuesta rápida no ejecuta dos avances sin una segunda intención explícita; una acción concurrente desde otro dispositivo se resuelve sin sobrescribir estados.

---

## 4. Segunda entrega: acceso, sesión y contratos

| ID | Corrección propuesta | Prueba de aceptación |
|---|---|---|
| **M1** | Excluir `dleche_session_cookies.xml` de respaldo y transferencia entre dispositivos; revisar también las reglas anteriores a Android 12 y el manifiesto fusionado de release. | Un dispositivo restaurado o migrado exige autenticación; el dispositivo original conserva su sesión válida. |
| **M3** | Primero verificar la IP efectiva. Establecer una cabecera saneada por el proxy y confiar en ella únicamente desde proxies autorizados. Después reemplazar el bloqueo duro global por email por controles combinados IP+cuenta, manteniendo protección por IP y detección de ataques distribuidos. | Fallos desde un origen no bloquean indiscriminadamente a usuarios legítimos; no se puede evadir el límite falsificando headers. Cubrir redes compartidas, IPv4/IPv6 y `Retry-After`. |
| **M4** | Reproducir `sesión válida → POST inválido → 400 → corrección → POST`, observando cookies, bootstrap y filtros de seguridad. Corregir la causa y coordinar la recuperación CSRF concurrente en los clientes. | La segunda operación llega a validación o se completa; no devuelve un 403 espurio. La rotación legítima por login/logout sigue funcionando. |
| **M5** | Formalizar la ventana de entrega y aplicarla en el servidor, con zona horaria y tolerancia definidas. Reflejar disponibilidad en la app. | Casos antes del inicio, en el límite, durante la franja y después del cierre; códigos repetidos y pago contra entrega. |
| **B1** | Verificar la solución existente en el APK objetivo. | Un 401 durante el uso muestra el aviso; un logout voluntario no lo muestra; no quedan datos de la sesión anterior. |
| **B2** | Llevar el cierre de sesión a un elemento compartido de navegación del admin, conservando confirmación. | Accesible desde todas las pestañas y con limpieza de estado. |
| **B3** | Unificar la escritura JSON de errores con codificación UTF-8 antes de obtener el writer. | Mensajes con tildes correctos en respuestas 401/403/429, verificando bytes y headers. |
| **B4** | Distinguir términos no aceptados de versión ausente u obsoleta; actualizar el manejo web y el contrato si cambia el código de error. | Cada caso tiene feedback útil y se permite volver a aceptar la versión vigente. |
| **B6** | Tratar explícitamente parámetros obligatorios ausentes y errores de binding. `GlobalExceptionHandler` tiene un fallback 500 y no contempla `MissingServletRequestParameterException`. | Disponibilidad sin fecha o con fecha inválida devuelve 400; una consulta válida mantiene su respuesta. |
| **B7** | Definir formato telefónico admitido, normalizar y validar en backend; reflejar la misma regla en la app. | `abc` y valores malformados se rechazan sin persistir; números admitidos se guardan correctamente. |

**Dependencia crítica:** verificar y corregir M3 antes de activar polling generalizado. Si varias personas comparten la clave de rate limit por error, agregar consultas periódicas agrava el problema.

---

## 5. Tercera entrega: evidencia, información y calidad de uso

### B8 — incidencias

Dividirlo en dos entregables:

**Validación textual**
- Mostrar “Describa la incidencia” cuando corresponda.
- Explicar por qué no puede enviarse y conservar el borrador ante errores.
- Mantener el límite de longitud alineado con backend.

**Evidencia fotográfica**
- Definir un contrato compatible con la creación textual existente.
- Implementar carga, asociación y consulta autorizada de la imagen.
- Validar contenido real, formato, tamaño y dimensiones; revisar almacenamiento privado y limpieza de cargas incompletas.
- Agregar selección, previsualización, eliminación y progreso en Android.
- Permitir que el administrador consulte la evidencia.

**Aceptación:** texto solo, texto con imagen, archivo inválido, interrupción de red, reintento sin incidencias duplicadas y rechazo de acceso a evidencias de pedidos ajenos.

### B9 — detalles del pedido y textos

- Unificar el mensaje de código validado para evitar duplicación.
- Habilitar el cobro según `linkAvailable`; mostrar cuándo estará disponible.
- Excluir la franja actual de la reasignación usando su identidad operativa completa.
- Consumir `pricingSnapshot` para mostrar subtotal, tarifa y total históricos. **No deducir la tarifa restando importes ni usar la tarifa actual.**
- Incorporar los datos comerciales necesarios para preparar packs, combos y personalizaciones.
- Mostrar el estado de reembolso disponible en el contrato; diferenciar **solicitado/iniciado** de **completado**.
- Corregir plurales y frases mediante recursos de texto.

### B10 — web pública

- Corregir el uso de `.secure-note` en textos extensos; permitir salto de línea y ajustar contenedores.
- Verificar `/pagar/{token}` a 320, 360, 390 y 412 px, con textos largos y aumento de fuente.
- Formatear fechas para el público local sin desplazar el día por conversión de zona horaria.
- Sustituir los contactos del footer por datos aprobados; ocultar los que aún no estén disponibles.

### B5 — release Android

- Activar optimización y ofuscación con las reglas necesarias para las dependencias utilizadas.
- Verificar serialización, Retrofit, QR y flujo de sesión sobre el **APK release**.
- Conservar el archivo de mapeo para diagnosticar fallos.
- Revisar firma, `versionCode`, URL de API y compatibilidad de CAPTCHA.

El build probado fue debug. Además, el valor por defecto de API en release es distinto del dominio de QA: el destino efectivo debe comprobarse al generar el artefacto.

---

## 6. Pruebas obligatorias para cerrar la contingencia

### Matriz funcional

Cubrir las cinco combinaciones operativas del informe:

- ADMIN.
- DELIVERY_HANDLER para recojo.
- DELIVERY_HANDLER para entrega interna.
- DELIVERY.
- DELIVERY_COORDINATOR.

Para cada una: cola vacía y con datos, asignación/reasignación, reanudación, pérdida de conexión, sesión revocada y acceso a pedidos fuera de su alcance.

### Flujos todavía pendientes

| Flujo | Verificación necesaria |
|---|---|
| **DELIVERY con pedidos** | Asignar, visualizar, avanzar, reportar incidencia y completar entrega, incluyendo reasignación concurrente. |
| **Pago contra entrega** | Código correcto sin pago, pago aprobado con código registrado y respuesta de pago incierta. Un único cobro y cierre coherente. |
| **Devoluciones/reembolsos** | Solicitud, procesamiento, fallo, reintento y confirmación; conciliación con la pasarela y reposición de stock según la regla aplicable. |
| **Perdido automático** | Reloj controlado alrededor de 24 h, causa de incidencia, pago aprobado, reinicio del plazo cuando corresponda y concurrencia con una decisión administrativa. |
| **Packs/combos/personalización** | Snapshot comprado, precio, componentes, stock y datos visibles para preparación. |
| **Horarios universitarios** | Cupo independiente, agotamiento, franja compartida, universidad incorrecta y compatibilidad de intentos anteriores. |

**Capas de prueba:** unitarias de reglas y estado; integración HTTP/persistencia para seguridad, checkout y concurrencia; UI Android para gestos y navegación; navegador móvil para pago público; recorrido integrado contra la pasarela en modo de pruebas.

---

## 7. Despliegue y reversión

### Preparación

- Identificar versión del código, imagen desplegada, migraciones y APK realmente probado.
- Preservar los cambios existentes del servidor: `docker-compose.yml` tiene modificaciones preparadas y sin preparar.
- Establecer una base reproducible de la app local, cuya carpeta no contiene metadatos Git.
- Respaldar datos y configuración antes de cambiar horarios o contratos.
- Identificar responsables nominales de backend, Android, web, infraestructura, QA y operación.

### Orden recomendado

1. Verificación y corrección de proxy/rate limit.
2. Backend compatible: A3 y contratos/errores.
3. APK piloto: actualización de listas y protección de avances.
4. Web y mejoras de información.
5. Restitución controlada de horarios por universidad.
6. Ampliación gradual tras un turno operativo completo satisfactorio.

La evidencia fotográfica puede incorporarse después del parche urgente, pero debe completar su propio circuito backend–app–admin.

### Reversión

| Componente | Procedimiento |
|---|---|
| **Backend/web** | Volver al artefacto anterior identificado, siempre que siga siendo compatible con los datos y contratos introducidos. |
| **Android** | Preparar una versión de recuperación firmada con la misma clave y `versionCode` superior. No depender de una reinstalación que borre datos. |
| **Horarios** | Detener nuevas reservas afectadas y restaurar oferta solo tras conciliar pedidos y capacidad. No borrar franjas comprometidas. |
| **Pagos/estados** | Consultar el resultado autoritativo y conciliar. Una reversión de software no deshace cobros, entregas ni notificaciones. |

**Disparadores de reversión:** duplicidad de cobro, avance no autorizado, pérdida de acceso generalizada, bloqueo nuevo de checkout o inconsistencia de capacidad. Para degradaciones de rendimiento, comparar con una línea base y límites acordados antes del despliegue.

---

## 8. Criterios de cierre y seguimiento

La contingencia se cierra cuando:

- A1, A2, A3 y M2 pasan sus escenarios de aceptación en el artefacto candidato.
- M3 tiene IP efectiva verificada y aislamiento de límites demostrado.
- M4 está reproducido y resuelto, o descartado con evidencia de la secuencia completa.
- M1 pasa la verificación de transferencia/restauración.
- Los recorridos críticos de pago, entrega y reembolso están validados.
- Cada hallazgo tiene responsable, evidencia y versión de resolución; las decisiones pendientes quedan explícitas.
- Un turno piloto termina sin pedidos invisibles, avances accidentales ni inconsistencias financieras.

Durante el piloto y las primeras 48 horas, revisar: errores de checkout por código, 401/403/429, frescura de colas, transiciones conflictivas, pagos pendientes/desconocidos, reembolsos pendientes y retrasos del procesamiento de incidencias. Registrar identificadores de correlación, sin cookies, códigos de entrega ni tokens de pago.

**Orden de trabajo propuesto:** contención → acceso y operación crítica → contratos y sesión → información/evidencias → validación integrada → despliegue gradual.

La decisión de negocio que falta para cerrar M5 es: **¿la validación del código debe bloquearse hasta el inicio de la franja, o quieren permitir entregas anticipadas?**
