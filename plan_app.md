# Plan de correcciones de la aplicación móvil

> **Objetivo:** corregir y verificar la app Android del personal, punto por punto, a partir del [informe E2E](docs/INFORME_E2E_DLECHE.md). Este documento es planificación: ninguna casilla representa una corrección ejecutada.

**Fecha:** 2026-10-31. **Ámbito:** administrador, operador, encargado y delivery dentro de Android. El panel administrativo web y la tienda pertenecen a [plan_dleche.md](plan_dleche.md).

## 1. Base de trabajo y prioridades

El informe corresponde a `main @ ed581dc` con un permiso agregado localmente y a un backend en Flyway V77. Se contrastaron los puntos principales con el código móvil disponible; no se volvió a ejecutar el E2E ni se inspeccionó el código del backend o de la web.

| Prioridad propuesta | Significado |
|---|---|
| P0 | Bloquea el acceso o el uso básico; atender primero. |
| P1 | Afecta la operación, la coherencia de estados o una integración relevante. |
| P2 | Claridad de interfaz, validaciones y endurecimiento con alcance acotado. |
| P3 | Cobertura diferida por una dependencia o hito explícito. |

Estas prioridades ordenan el trabajo; no sustituyen la severidad del informe. Su resumen menciona tres hallazgos medios, pero la sección correspondiente enumera cuatro. Se utilizarán los identificadores individuales como referencia.

### Evidencia que cambia la planificación

| Hallazgo | Estado observado en el repositorio | Tratamiento |
|---|---|---|
| APP-01 | `ACCESS_NETWORK_STATE` ya está en el manifest como modificación local. | Verificar y consolidar posteriormente, sin duplicar el permiso. |
| APP-04 / APP-07, proveedor | `saveProvider()` ya llama a `isValidProviderPhone()` y `ProviderCard` ya muestra «Proveedor guardado.». | Reproducir con una compilación identificada y distinguir datos antiguos de nuevas escrituras. |
| SEC-04 | El interceptor BASIC se agrega bajo `debugLogging`; `AppContainer` pasa `BuildConfig.DEBUG`. | Verificación de release; modificar solo si aparece una vía que lo active indebidamente. |
| APP-05 | `OrderBlocks()` cuenta por coincidencia de estado, sin fecha ni hora. | Defecto confirmado por lectura; corrección pendiente. |
| Alertas | El chequeo periódico de fondo y el polling de colas en primer plano tienen interruptores diferentes. | Verificar cada mecanismo por separado; no asumir que M3 desactiva ambos. |

### Preparación común

- [ ] Registrar commit, variante, versión instalada, URL del backend y configuración de alertas de cada ejecución futura.
- [ ] Conservar los cambios locales existentes y las eliminaciones de documentos detectadas; no utilizarlos como parte de una corrección nueva sin revisión.
- [ ] Preparar pedidos QA por rol, modalidad, estado, pago y franja. Usar datos reproducibles en vez de depender de fechas absolutas del informe.
- [ ] Crear una ficha de seguimiento por tarea: responsable, estado, evidencia, dependencia y criterio pendiente.
- [ ] Usar los tests existentes como base y añadir cobertura de comportamiento donde el fallo lo justifique.

## 2. Tareas detalladas

### M-01 — Confirmar el arreglo del cierre tras login · APP-01 · P0

**Objetivo:** que todos los roles puedan iniciar y restaurar sesión sin una `SecurityException` al programar alertas.

**Puntos de entrada:** `app/src/main/AndroidManifest.xml`, `notifications/OrderAlertService.kt`, `MainActivity.kt`. Las rutas Kotlin abreviadas de este documento parten de `app/src/main/java/com/grupo4/dlecheymielapp/`.

- [ ] Revisar el cambio local y verificar que el permiso esté declarado una sola vez en el manifest fusionado de las variantes utilizadas.
- [ ] Comprobar la programación de `OrderAlertScheduler` después del login y al restaurar una sesión persistida.
- [ ] Verificar que la programación repetida no duplique el job y que cerrar sesión o cambiar de usuario respete el ciclo de vida previsto.
- [ ] Repetir acceso con administrador, operador, encargado y delivery; cubrir instalación limpia y actualización de una instalación existente.
- [ ] Verificar arranque sin red y regreso de la conectividad, sin convertir un fallo de alertas en un fallo de acceso.

**Aceptación:** los cuatro roles llegan a su pantalla inicial, la sesión restaurada funciona y no aparece la excepción reportada. La evidencia identifica la variante y el dispositivo.

**Dependencia:** ninguna de backend. La incorporación definitiva del cambio local corresponde a la fase de implementación.

### M-02 — Corregir «Para entregar ahora» · APP-05 · P1

**Objetivo:** que el contador represente pedidos operables en el momento actual y no solo pedidos en un estado determinado.

**Puntos de entrada:** `ui/handler/HandlerOrdersScreen.kt`, `OrderBlocks()`, modelos de horario y tarjetas operativas compartidas.

- [ ] Definir con negocio el significado de «ahora»: fecha, inicio y fin de franja, estados admitidos, incidencias abiertas y tratamiento de pedidos atrasados.
- [ ] Confirmar la zona horaria del negocio con backend; evitar que cambiar la zona del teléfono cambie arbitrariamente el resultado.
- [ ] Separar el cálculo de elegibilidad de la composición visual y permitir un reloj controlable en pruebas.
- [ ] Excluir días futuros y franjas de hoy que no han comenzado. Definir explícitamente el comportamiento al finalizar la franja y ante un horario ausente o inválido.
- [ ] Decidir si el contador representa toda la cola cargada o el resultado filtrado; mantener el mismo criterio en todas las pantallas.
- [ ] Revisar si la cola está paginada antes de presentar el número como total global. No ampliar el alcance a una nueva paginación salvo que afecte ese contrato visual.
- [ ] Recalcular al actualizar, regresar del segundo plano y cruzar el límite horario con la pantalla abierta, sin activar polling de red pendiente de M3.
- [ ] Aplicar el criterio compartido a delivery, operador y encargado donde aparezca este indicador.
- [ ] Cubrir con pruebas: ayer, hoy antes del inicio, instante de inicio, dentro de franja, instante de fin, mañana, terminales, incidencia abierta y cambio de zona del dispositivo.

**Aceptación:** un pedido del 4 de noviembre no cuenta el 31 de octubre y uno de las 18:00 no cuenta antes de esa hora. Los límites y atrasos siguen la regla documentada.

**Dependencia:** definición temporal de B-02 en el plan general. El significado del contador se acuerda por separado del permiso para validar códigos anticipadamente.

### M-03 — Distinguir subtotal y precio unitario · APP-02 · P2

**Puntos de entrada:** `ui/admin/AdminOrderDetailScreen.kt`, `PurchaseLine()`, `AdminOrderItem` y desglose de precios.

- [ ] Cambiar la presentación a un texto inequívoco, por ejemplo: «2 × Rol de canela — Subtotal: S/ 12.00».
- [ ] Mostrar precio unitario solo si el contrato entrega un valor fiable para esa línea. No obtenerlo dividiendo el subtotal si existen promociones, packs o ajustes.
- [ ] Conservar orden de líneas, cantidades, detalles de preparación, descuentos y cargos separados según los datos del pedido.
- [ ] Revisar otros lugares móviles que representen la misma línea para mantener la terminología.
- [ ] Verificar cantidades 1 y mayores a 1, importes decimales, nombres largos y tamaño de fuente aumentado.

**Aceptación:** se reconoce sin ambigüedad qué importe corresponde a toda la línea; los importes y totales del servidor permanecen intactos.

**Dependencia:** ninguna para el cambio de etiqueta; combos y promociones reales se verifican en M-13.

### M-04 — Mostrar la asignación actual en el detalle · APP-03 · P2

**Puntos de entrada:** `ui/admin/AdminOrderDetailScreen.kt`, `AssignmentCard()`, `ui/admin/AdminOrderDetailViewModel.kt`, `data/admin/AdminRepository.kt`.

- [ ] Identificar qué consulta existente entrega la asignación de encargado y cuál entrega el destino de delivery, incluidos los casos de operador o proveedor que permita el contrato.
- [ ] Cargar la asignación al abrir el detalle, sin obligar a abrir el diálogo de edición.
- [ ] Mostrar nombre y tipo de destino, «Sin asignar», carga o error recuperable. No representar un error de consulta como ausencia de asignación.
- [ ] Actualizar tarjeta, diálogo y lista tras asignar, cambiar o retirar la asignación y después de reprogramar una franja.
- [ ] Evitar que una respuesta anterior sobrescriba una asignación recién guardada al refrescar.
- [ ] Revisar qué eventos devuelve el historial: renderizar los existentes; si faltan, registrar la dependencia de B-03.
- [ ] No fabricar eventos históricos desde la app ni inferir quién realizó una asignación a partir de la sesión actual.
- [ ] Verificar pedido sin encargado, con encargado, destino de delivery cambiado, error parcial y reprogramación que elimina la asignación.

**Aceptación:** el responsable actual se conoce desde el detalle sin abrir diálogos. El historial solo presenta eventos respaldados por el servidor.

**Dependencia:** la tarjeta puede avanzar con endpoints existentes; el historial completo depende de B-03 si faltan eventos.

### M-05 — Dar respuesta al guardar una nota vacía · APP-04 · P2

**Puntos de entrada:** `ui/admin/AdminOrderDetailViewModel.kt`, `submitNote()` y diálogo de notas.

- [ ] Tratar texto vacío y solo espacios de la misma manera; actualmente `submitNote()` retorna sin mensaje.
- [ ] Mostrar una indicación junto al campo y mantener una regla coherente entre botón, teclado y validación del ViewModel.
- [ ] Limpiar el error al corregir el texto y conservarlo cuando falle la red para permitir un reintento consciente.
- [ ] Mantener el límite existente de longitud y la confirmación «Nota guardada.» para escrituras exitosas.
- [ ] Verificar que el doble toque no cree notas duplicadas y que un error no se anuncie como éxito.

**Aceptación:** una nota vacía no realiza una escritura y el usuario entiende cómo continuar; una válida se guarda una sola vez y confirma el resultado.

### M-06 — Reproducir y cerrar los problemas del proveedor · APP-04 / APP-07 · P2

**Puntos de entrada:** `ui/handler/OrderDetailViewModel.kt`, `ProviderCard` en `OrderDetailScreen.kt`, `ui/common/FormRules.kt`, `data/fulfillment/DeliveryRepository.kt`.

**Estado:** ya existen validación de teléfono y mensaje de éxito. El informe por sí solo no permite concluir si `abc` llegó desde datos previos, una escritura externa o la compilación probada.

- [ ] Reproducir en la compilación actual el ingreso y guardado de `abc`, registrando si se intenta enviar la petición.
- [ ] Distinguir el valor precargado del servidor de uno introducido y guardado en la app.
- [ ] Acordar con backend el formato aceptado: prefijo internacional, separadores, longitud y obligatoriedad. Evitar reglas locales incompatibles con B-04.
- [ ] Revisar que el campo inválido explique el formato y que el mismo control se aplique al enviar desde cualquier acción disponible.
- [ ] Reproducir el guardado válido y verificar que «Proveedor guardado.» sea visible; si desaparece por recarga o recomposición, corregir únicamente ese flujo.
- [ ] Reiniciar el estado de éxito al editar, mostrar el error de servidor y conservar lo escrito si falla la conexión.
- [ ] Cubrir espacios, letras, valor vacío, formatos válidos acordados, doble toque, error remoto y carga de un proveedor antiguo inválido.
- [ ] Ampliar `ProviderPhoneRuleTest` y `OrderDetailFormsTest` solo donde falte cobertura del comportamiento reproducido.

**Aceptación:** `abc` no se envía desde la app actual; el guardado exitoso confirma y persiste al reabrir. Si no se reproduce, cerrar con evidencia de versión y datos, sin reimplementar lo existente.

**Dependencia:** B-04 valida todas las escrituras en servidor y define el tratamiento de datos heredados.

### M-07 — Representar correctamente un código ya validado · APP-06 · P1

**Puntos de entrada:** `ui/handler/OrderDetailScreen.kt`, `HandoffCard()`, `OrderDetailViewModel.kt`; revisar también las tarjetas de lista y `CardActions.kt`.

- [ ] Modelar la diferencia entre «código validado» y «pedido entregado». Un pedido contra entrega puede tener el código validado y seguir esperando pago.
- [ ] Tras éxito, limpiar el campo y retirar o deshabilitar su envío con un mensaje de estado apropiado.
- [ ] Usar el estado persistido del servidor —por ejemplo, `payAtDelivery.codeVerified` cuando esté disponible— para reconstruir la pantalla al reabrirla.
- [ ] Para prepago entregado, mostrar el estado final; para contra entrega pendiente de pago, mostrar la siguiente acción permitida sin repetir la validación.
- [ ] Si el servidor no expone el estado necesario para algún flujo, pedirlo en B-02 antes de resolverlo únicamente con una variable temporal.
- [ ] Cubrir respuesta exitosa seguida de error al refrescar, doble envío, reapertura, rotación y actualización del pedido desde otro cliente.
- [ ] Conservar el cierre sin código del operador en los casos donde el contrato lo permite.

**Aceptación:** no se solicita de nuevo un código ya confirmado; el pago pendiente sigue visible y el pedido no se presenta como entregado prematuramente.

**Dependencia:** estado de validación disponible en la API. Usar el caso del pedido 17 como escenario, recreándolo si ya no es reproducible.

### M-08 — Limpiar errores que ya no corresponden · APP-08 · P1

**Puntos de entrada:** estado y acciones de `ui/handler/OrderDetailViewModel.kt`, `OrderDetailScreen.kt` y acciones equivalentes de tarjeta.

- [ ] Reproducir: código incorrecto → mensaje rojo → incidencia registrada con éxito → recarga del pedido.
- [ ] Separar errores de carga, entrega, incidencia, proveedor y pago; limpiar cada uno cuando su acción se corrija o deje de aplicar.
- [ ] Al registrar una incidencia que impide entregar, retirar el error anterior del código y mostrar la situación de incidencia abierta.
- [ ] Evitar que una respuesta tardía de entrega reponga un error obsoleto después de cambiar el estado del pedido.
- [ ] No ocultar errores de acciones que siguen pendientes o que realmente fallaron durante la transición.
- [ ] Cubrir el flujo reportado con una prueba de estado y verificar reapertura, rotación y recarga fallida.

**Aceptación:** tras una incidencia exitosa solo se muestran mensajes vigentes; no queda «El código no es válido» asociado a una entrega que ya no está habilitada.

### M-09 — Evitar el distrito duplicado · APP-09 · P2

**Puntos de entrada:** `data/fulfillment/DeliveryRepository.kt`, conversión `DeliveryDetail.toWork()`, `SummaryCard()` y «Copiar dirección».

- [ ] Comparar `delivery.address` y `delivery.districtName` en respuestas reales para determinar dónde se introduce la repetición.
- [ ] Acordar con B-05 si `address` contiene dirección completa o solo vía y número.
- [ ] Preferir mostrar dirección y distrito en campos separados si el contrato actual no permite una composición inequívoca.
- [ ] Si se mantiene una línea compuesta, usar una regla conservadora para el sufijo; no eliminar coincidencias arbitrarias dentro de una avenida, urbanización o referencia.
- [ ] Mantener la misma dirección canónica al mostrar y copiar, preservando las referencias.
- [ ] Verificar distrito ausente, dirección vacía, espacios, diferencias de mayúsculas, distrito ya al final y nombre de vía que contiene el mismo texto.

**Aceptación:** «Av. Los Alisos 123, Los Olivos» no incorpora un segundo «Los Olivos» y ninguna parte válida de la dirección se pierde.

### M-10 — Alinear código y cobro con la regla de franja · APP-10 · P1, decisión pendiente

**Objetivo:** expresar una política de negocio explícita sin imponerla unilateralmente desde Android.

- [ ] Solicitar una decisión por rol y modalidad: permitir la validación anticipada como prevalidación, o impedirla hasta el inicio de franja.
- [ ] Especificar si también cambia el cierre sin código del operador y cuáles son las excepciones autorizadas, si existen.
- [ ] Si se permite anticipar, diferenciar «Código validado; pendiente de franja/pago» de «Entregado» y explicar por qué el QR/link todavía no está disponible.
- [ ] Si se restringe, reflejar en la UI el bloqueo y el momento de habilitación utilizando la regla y el error de backend.
- [ ] No usar únicamente el reloj local como mecanismo de control: la API debe aplicar la política.
- [ ] Verificar antes del inicio, justo al inicio, dentro y después de la franja, reprogramación, prepago y contra entrega.

**Aceptación:** app y backend comparten una política documentada; no hay contradicción entre el estado mostrado, la validación y el cobro.

**Dependencia bloqueante:** B-02. No declarar APP-10 como error corregido hasta que negocio resuelva la política.

### M-11 — Integrar límites de intentos y respuestas 429 · SEC-01 / F-10 · P1

- [ ] Solicitar a B-01 y B-12 contrato de bloqueo: código de error, alcance, duración y señal de reintento.
- [ ] Diferenciar código inválido, bloqueo temporal, sesión vencida y fallo de conectividad en detalle y tarjetas.
- [ ] Mostrar tiempo de espera cuando el servidor lo entregue; contemplar ausencia o formato no válido de la señal sin entrar en un bucle de reintentos.
- [ ] Evitar envíos dobles y mantener la espera coherente al reabrir la pantalla; el servidor sigue siendo la autoridad aunque se reinicie la app.
- [ ] Mantener el tratamiento actual de fallos de login e integrar el 429 remoto sin contradecir el límite local.
- [ ] Si se aprueba CAPTCHA en backend, planificar antes la compatibilidad del login móvil: la configuración actual indica que el flujo correspondiente todavía no está construido.
- [ ] Verificar bloqueo por pedido, otro pedido disponible, expiración del bloqueo, reinicio, login desde red compartida y pérdida de red durante la espera.

**Aceptación:** la app informa y respeta el bloqueo comunicado. Un contador local no se presenta como solución de SEC-01.

**Dependencia:** B-01 / B-12; la ausencia de CAPTCHA no implica incorporarlo automáticamente.

### M-12 — Verificar sesión, logs y rotación CSRF · SEC-03 / SEC-04 / nota CSRF · P2

**Puntos de entrada:** `core/network/PersistentCookieJar.kt`, `NetworkModule.kt`, `CsrfInterceptor.kt`, `AppContainer.kt`.

#### M-12.1 — Protección del almacenamiento de sesión

- [ ] Evaluar almacenamiento cifrado respaldado por Android Keystore, compatibilidad del proyecto y tratamiento de fallos antes de elegir una implementación.
- [ ] Conservar el contrato `CookieStorage` y la semántica de dominio, ruta, expiración, `Secure` y `HttpOnly`.
- [ ] Definir migración de las cookies actuales: transferir y eliminar el texto anterior solo después de guardar correctamente, o invalidar la sesión con una explicación y solicitar nuevo login.
- [ ] Ante pérdida o invalidación de clave, recuperar mediante nueva autenticación; evitar persistencia silenciosa en texto claro como alternativa.
- [ ] Verificar persistencia, logout, cambio de usuario, actualización de versión, datos corruptos y exclusiones de respaldo.

**Aceptación:** no queda la cookie en claro en el almacenamiento anterior y una sesión irrecuperable no provoca un cierre inesperado. Documentar que cifrar en reposo no garantiza protección absoluta en un dispositivo comprometido.

#### M-12.2 — Confirmación del logging por variante

- [ ] Verificar el enlace ya existente entre `BuildConfig.DEBUG` y `debugLogging` en la compilación release utilizada.
- [ ] Confirmar ausencia de trazas HTTP del interceptor en release y revisar que no existan otros logs de sesión o enlaces de pago.
- [ ] Revisar también URLs y parámetros de debug: BASIC omite cuerpos, pero registra URLs.
- [ ] Cerrar SEC-04 como verificado si la evidencia confirma el comportamiento; cambiar código solo ante una exposición reproducible.

**Aceptación:** release no activa el logging HTTP y no se registran cookies, códigos o enlaces sensibles.

#### M-12.3 — Regresión de CSRF

- [ ] Verificar que una escritura use el token vigente y que el `Set-Cookie` de su respuesta alimente la siguiente escritura.
- [ ] Cubrir dos escrituras consecutivas, token ausente, token vencido y acciones simultáneas permitidas por la UI.
- [ ] Distinguir un 403 por CSRF de uno por permisos cuando el contrato lo permita.
- [ ] Si aparece una carrera por rotación, acordar la solución con B-13; no reenviar automáticamente una operación no idempotente.

**Aceptación:** las escrituras consecutivas funcionan tras la rotación, sin duplicar notas, incidencias ni operaciones de pago.

### M-13 — Completar cobertura móvil pendiente · P1 / P3 según dependencia

| Bloque | Tareas | Criterio de aceptación | Dependencia |
|---|---|---|---|
| Combos, packs y promociones | Preparar catálogo QA autorizado; verificar líneas, cantidades, preparación y totales en las vistas móviles correspondientes. | Sin ambigüedad de importes ni pérdida de componentes. | B-14; no crear catálogo sin autorización. |
| Contra entrega, operador y encargado | Usar una franja vigente; generar QR/link; cubrir código antes/después del pago, rechazo, espera y aprobación. | Estado final correcto, sin doble entrega ni solicitud repetida de código. | M-07, M-10 y fixtures de B-14. |
| Incidencias | Probar reembolso, desistimiento y dar por perdido en combinaciones autorizadas; revisar confirmaciones y recarga. | La app refleja el resultado real, los errores y las acciones permitidas. Stock y dinero se contrastan con backend. | B-14 y entorno de pagos de prueba. |
| Pedido 17 | Comprobar su estado antes de reutilizarlo; si ya cambió, reproducir con otro pedido el caso código validado + pago pendiente. | El flujo termina correctamente en su franja según la política vigente. | B-02; no cerrar el pedido como parte de esta planificación. |
| Alertas de fondo | Verificar permiso de notificación, sesión vigente, llegada de trabajo, reapertura y aislamiento entre usuarios. | Sin duplicados, datos de otro usuario ni crash; sin prometer entrega inmediata. | M-01; el código actual usa consultas periódicas, no demuestra push remoto. |
| Polling de primer plano | Confirmar alcance y cierre de M3 antes de habilitarlo; medir recuperación y carga con su configuración prevista. | Sin agotar límites ni degradar la operación. | B-12; P3 hasta resolver M3. |

## 3. Orden de ejecución recomendado

1. **Base y acceso:** preparación y M-01. Registrar la compilación que servirá para todas las reproducciones.
2. **Operación diaria:** acordar criterios de M-02 y ejecutar M-02, M-07 y M-08. Pedir desde el inicio las decisiones de B-01/B-02.
3. **Claridad y formularios:** M-03, M-04 —tarjeta actual—, M-05, M-06 y M-09.
4. **Dependencias de servidor:** completar M-10, M-11 e historial de M-04 cuando estén listos los contratos correspondientes.
5. **Sesión y verificación de release:** M-12; las comprobaciones de logs y CSRF pueden comenzar antes de terminar las integraciones.
6. **Cierre E2E:** M-13 y regresión completa. Mantener pendiente explícito lo que dependa de M3 o datos no autorizados.

No se proponen duraciones sin resolver primero la política temporal y las discrepancias de proveedor. Cada bloque debe poder cerrarse con evidencia independiente.

## 4. Validación y criterio global de cierre

Las siguientes comprobaciones se ejecutarán **durante la implementación**, no durante la creación de este plan:

- [ ] Ejecutar pruebas de comportamiento focalizadas en contador, estados de código/incidencia, validación de proveedor, cookies y CSRF.
- [ ] Reutilizar suites existentes como `AdminOrderDetailViewModelTest`, `OrderDetailViewModelTest`, `OrderDetailFormsTest`, `OpenIncidentHandoffTest`, `ProviderPhoneRuleTest` y `FieldRolesRepositoryTest` según el área modificada.
- [ ] Ejecutar las tareas Gradle de pruebas unitarias, lint y compilación pertinentes; por ejemplo `bash gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`, con el entorno Android configurado.
- [ ] Verificar también una compilación release para permiso, logging y comportamiento con optimización.
- [ ] Ejecutar pruebas manuales por los cuatro roles: login, sesión persistida, colas, búsqueda, asignación, estados, notas, código, pago e incidencias.
- [ ] Repetir sin red, recuperación, rotación, segundo plano y reapertura en el Xiaomi del informe o documentar el dispositivo sustituto.
- [ ] Preservar lo ya verificado: aislamiento de roles, totales del servidor, doble envío controlado y manejo de datos desactualizados.
- [ ] Adjuntar por hallazgo versión, pasos, resultado esperado, resultado obtenido y evidencia sin credenciales ni códigos reutilizables.

**Cierre:** APP-01 a APP-09 deben tener corrección verificada o no reproducción explicada; APP-10 debe tener decisión y evidencia; SEC-03 debe tener resultado de endurecimiento o decisión explícita documentada; SEC-04 debe verificarse en release. Las dependencias SEC-01, F-10, historial y M3 se registran como pendientes si no están disponibles: no se consideran resueltas por cambios visuales.

## 5. Dependencias que se derivan al plan general

| Tema móvil | Responsable externo / referencia |
|---|---|
| Intentos de código | Backend B-01. |
| Regla de franja y estado persistente de validación | Backend y negocio B-02. |
| Historial de asignación | Backend B-03. |
| Validación de proveedor | Backend B-04. |
| Contrato de dirección | Backend B-05 y frontend W-05. |
| Límites, CAPTCHA y M3 | Backend B-12, frontend W-03. |
| Cookies y CSRF | Backend B-07/B-13, frontend W-03. |
| Fixtures y escenarios sin probar | Backend B-14, frontend W-06. |

Los hallazgos de checkout, 3DS y tienda web se desarrollan en `plan_dleche.md`; solo se incorporan a Android sus contratos y efectos sobre los flujos del personal.
