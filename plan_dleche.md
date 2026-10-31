# Plan general de correcciones — Backend y frontend web

> **Objetivo:** organizar el trabajo externo a la aplicación móvil identificado en el [informe E2E](docs/INFORME_E2E_DLECHE.md), separado por backend y frontend. La planificación detallada de Android está en [plan_app.md](plan_app.md).

**Fecha:** 2026-10-31. **Estado:** tareas propuestas; ninguna implementada o probada en esta sesión. El backend y la web no se inspeccionaron directamente: las causas del informe pendientes de confirmar se mantienen como hipótesis.

**Prioridad propuesta:** P1 = operación, contratos y comprobaciones relevantes antes de liberar el flujo afectado; P2 = corrección acotada, claridad y endurecimiento; P3 = trabajo condicionado a un hito. No se deduce una vulnerabilidad crítica de una observación en sandbox.

## 1. Backend

### B-01 — Verificar y limitar intentos de código · SEC-01 · P1

- [ ] Revisar todas las rutas de validación utilizadas por operador, delivery y encargado, así como cualquier ruta alternativa equivalente.
- [ ] Determinar si ya existe un límite y por qué siete intentos no lo activaron. Registrar el resultado antes de cambiar la política.
- [ ] Definir umbral, ventana, espera y ámbito de bloqueo por pedido con controles complementarios por actor; no depender solo de la IP.
- [ ] Aplicar el control de forma atómica frente a peticiones simultáneas y coherente entre instancias.
- [ ] Definir reinicio del contador y tratamiento de código correcto, pedido reprogramado o pedido ya entregado; evitar un bloqueo permanente abusivo de pedidos ajenos.
- [ ] Exponer un error estable y señal de reintento para clientes; registrar el evento sin el código ni datos sensibles.
- [ ] Probar hasta el umbral acordado, concurrencia, expiración y separación entre pedidos y usuarios.

**Aceptación:** el límite se demuestra mediante pruebas, no puede eludirse reiniciando la app y dispone de recuperación definida. **Consumidor:** móvil M-11.

### B-02 — Definir la política temporal de entrega y cobro · APP-10 / APP-05 / APP-06 · P1

- [ ] Acordar por rol y modalidad si validar anticipadamente es una prevalidación permitida o una operación prohibida.
- [ ] Definir zona horaria, inicio/fin de franja, atrasos, reprogramaciones y cualquier excepción administrativa autorizada.
- [ ] Separar en el modelo validación de código, aprobación de pago y entrega efectiva; un éxito parcial no debe cerrar el pedido indebidamente.
- [ ] Aplicar la política en todos los endpoints relevantes, incluido cierre sin código del operador cuando corresponda.
- [ ] Exponer los estados o capacidades necesarios para reconstruir la UI después de reiniciar: código ya validado, pago pendiente y disponibilidad del link.
- [ ] Documentar errores y probar los límites temporales con reloj controlado, prepago, contra entrega y pedidos con incidencia.

**Aceptación:** política aprobada y consistente entre API y clientes. El contador «ahora» tiene una semántica definida aunque se permita prevalidación. **Consumidores:** M-02, M-07, M-10 y W-05.

### B-03 — Revisar asignación actual e historial · APP-03 · P2

- [ ] Confirmar que el contrato expone el encargado o destino actual con identificación y nombre suficientes para la app.
- [ ] Determinar si los cambios de asignación ya generan eventos y si faltan en la respuesta o solo en la presentación.
- [ ] Si faltan eventos, registrar asignación, cambio y retiro con actor, fecha, valor anterior y nuevo, junto con la actualización efectiva.
- [ ] Cubrir la eliminación de asignación causada por reprogramación; no inventar historial retroactivo sin evidencia.
- [ ] Verificar permisos de lectura y consistencia entre detalle, listado y diálogo.

**Aceptación:** el historial permite explicar los cambios futuros y el estado actual se consulta sin ambigüedad. **Consumidores:** M-04 y W-05.

### B-04 — Validar el proveedor en servidor · APP-04 / APP-07 · P2

- [ ] Reproducir una escritura de teléfono `abc` directamente contra la API y verificar si el dato observado era heredado.
- [ ] Definir formato, obligatoriedad y longitudes de nombre/teléfono; compartir las reglas con los clientes.
- [ ] Rechazar escrituras inválidas con un error de campo utilizable por la interfaz.
- [ ] Inventariar los registros antiguos inválidos y acordar su corrección sin inventar números ni sobrescribirlos automáticamente.
- [ ] Probar rutas por rol y confirmar que el proveedor sigue siendo información interna.

**Aceptación:** no se crean nuevos proveedores inválidos por acceso directo a la API. **Consumidores:** M-06 y W-05.

### B-05 — Aclarar la composición de direcciones · APP-09 · P2

- [ ] Determinar si el distrito duplicado nace en captura, persistencia, serialización o concatenación móvil.
- [ ] Documentar si `address` es una dirección completa o un componente separado de `districtName`.
- [ ] Corregir la fuente responsable y coordinar compatibilidad con clientes existentes.
- [ ] Evitar limpieza masiva por reemplazo de texto: revisar casos donde el distrito también integra el nombre de una vía o referencia.

**Aceptación:** el contrato produce una representación consistente sin perder información válida. **Consumidores:** M-09 y W-05.

### B-06 — Investigar aceptación de autenticación 3DS ficticia · F-01 · P1 de investigación

- [ ] Capturar la secuencia reproducible de sandbox y las respuestas de Culqi con datos sensibles ocultos.
- [ ] Confirmar con documentación vigente y, si hace falta, soporte del proveedor qué valida sandbox y qué garantías exige producción.
- [ ] Revisar cómo se vincula el resultado 3DS con el intento de checkout, token, importe y respuesta del proveedor; no tratar datos enviados por el navegador como prueba suficiente por sí solos.
- [ ] Verificar que el pedido se cree solo cuando el flujo de autorización exigido por la integración haya concluido correctamente.
- [ ] Cubrir datos ficticios, autenticación fallida, desafío cancelado, timeout, replay y respuestas fuera de orden según el contrato del proveedor.
- [ ] Si el comportamiento es exclusivo de sandbox, documentar esa limitación y el mecanismo de verificación para producción antes de habilitar pagos reales. No usar una compra real como prueba improvisada.

**Aceptación:** existe evidencia de qué ocurrió y de las garantías del flujo real; no se afirma que producción sea vulnerable ni segura por extrapolación del sandbox. **Dependencia frontend:** W-01.

### B-07 — Corregir respuestas HTTP, charset y cookies · F-02 / F-03 / F-04 · P1/P2

1. **Semántica HTTP — F-02, P1**
   - [ ] Revisar el manejo global de excepciones para no convertir método no permitido y tipo de contenido no admitido en errores 500.
   - [ ] Responder 405 con `Allow` donde corresponda, 415 para contenido no admitido y mantener el formato de error público.
   - [ ] Repetir DELETE `/client/orders/13`, PUT `/client/cart`, GET `/auth/profile` y solicitudes `text/plain`, preservando el orden previsto de autenticación y autorización.
2. **Cookies — F-03, P2**
   - [ ] Verificar `Set-Cookie` en el despliegue real, incluido el proxy HTTPS y la configuración por entorno.
   - [ ] Configurar `Secure` para la sesión servida por HTTPS y definir `SameSite` explícito según los flujos web necesarios.
   - [ ] Conservar `HttpOnly` de sesión y permitir lectura del token CSRF si el mecanismo del frontend lo requiere; revisar dominio, ruta y expiración.
   - [ ] Verificar login, logout y navegación de pago/3DS; no aplicar una política de cookies que rompa esos recorridos.
3. **Codificación — F-04, P2**
   - [ ] Unificar las respuestas 401/403 y demás errores como JSON en UTF-8 con cabeceras coherentes.
   - [ ] Verificar bytes y presentación de textos con tildes en Android y navegador.

**Aceptación:** los casos reportados devuelven el código HTTP correcto, las cookies cumplen la política acordada y «Sesión» se lee correctamente. **Dependencias:** W-03 y regresión móvil M-12.

### B-08 — Resolver divergencias de OpenAPI y validación de entrada · F-05 / F-06 · P1

- [ ] Comparar implementación, contrato publicado y clientes para decidir el comportamiento canónico de cada discrepancia.
- [ ] Resolver explícitamente los cuatro puntos de F-05:
  - carrito: obligatoriedad y ubicación de `productId`;
  - `checkout-config`: presencia y significado de `paperBagFee`;
  - consulta de pago inexistente: 204 frente a 404, con la semántica elegida;
  - falta de stock contra entrega: `CHECKOUT_REVALIDATION_REQUIRED` frente a `STOCK_UNAVAILABLE`.
- [ ] Actualizar API o especificación según la decisión, sin asumir que el documento o la implementación siempre tiene razón.
- [ ] Decidir si `checkout-attempts` rechaza campos desconocidos o si se modifica `additionalProperties:false` para reflejar una tolerancia intencional.
- [ ] Mantener totales y privilegios calculados por el servidor; aceptar campos extra no debe permitir asignación masiva.
- [ ] Añadir pruebas de contrato para respuestas vacías, errores y campos extra; publicar notas de compatibilidad para clientes.

**Aceptación:** especificación, API y clientes coinciden en todos los casos enumerados. **Consumidor principal:** W-02; Android solo en contratos que efectivamente utilice.

### B-09 — Revisar tratamiento de texto libre · F-07 · P2

- [ ] Clasificar campos como texto plano o contenido enriquecido y definir límites y caracteres de control admitidos.
- [ ] Revisar dónde se reutilizan en correos, exportaciones, registros y vistas web.
- [ ] Mantener el texto como dato y coordinar escape según el contexto de salida. No eliminar indiscriminadamente `<`, `>` o apóstrofes de nombres y direcciones.
- [ ] Si existe HTML permitido, definir una política específica de sanitización para ese contenido.
- [ ] Probar payloads persistidos en los consumidores reales junto con W-04.

**Aceptación:** una entrada controlada no se ejecuta en los consumidores probados. Guardar `<script>` como texto no se clasifica por sí solo como XSS confirmado.

### B-10 — Exponer motivos de rechazo utilizables · F-08 · P2

- [ ] Revisar si la respuesta actual ya contiene un motivo seguro y determinar si la pérdida está en API o interfaz.
- [ ] Mapear rechazos del proveedor a categorías públicas estables y acciones comprensibles, con un mensaje genérico para códigos desconocidos.
- [ ] Evitar devolver diagnósticos internos, datos de tarjeta o explicaciones que faciliten evadir controles antifraude.
- [ ] Mantener el resultado ya correcto: pago rechazado sin crear pedido y sin marcarlo como aprobado.

**Aceptación:** el frontend puede explicar el rechazo y orientar el siguiente paso sin interpretar mensajes internos de Culqi. **Consumidor:** W-02.

### B-11 — Revisar exposición de `providerOrderId` · F-09 · P2

- [ ] Identificar qué DTO, endpoints y roles reciben el identificador y cuáles necesitan usarlo.
- [ ] Evaluar la necesidad de exponerlo; su presencia no demuestra por sí sola una filtración de credenciales.
- [ ] Si no es necesario para clientes, retirarlo de respuestas públicas o sustituirlo por una referencia propia, conservándolo para conciliación autorizada.
- [ ] Revisar dependencias de frontend y especificación antes de cambiar la respuesta.

**Aceptación:** cada exposición tiene una necesidad documentada y los clientes no dependen innecesariamente del ID del proveedor. **Dependencia:** W-02.

### B-12 — Revisar límites por IP, 429 y M3 · F-10 · P1

- [ ] Medir el efecto de 10 logins/5 minutos y 20 checkout-attempts/10 minutos en redes compartidas; contrastar abuso y uso legítimo.
- [ ] Definir límites combinados por IP, cuenta, sesión u operación según el endpoint, manteniendo protección contra enumeración y abuso.
- [ ] Incorporar `Retry-After` a bloqueos temporales y un contrato de error común; probar su coherencia con la ventana aplicada.
- [ ] Revisar la configuración del proxy para identificar correctamente al cliente sin confiar en cabeceras manipulables.
- [ ] Evaluar CAPTCHA según necesidad y compatibilidad; si se habilita, coordinar previamente el login móvil y la accesibilidad web.
- [ ] Verificar por separado qué falta para cerrar M3 y qué límites afectan a las colas. No asumir que corregir login y checkout resuelve ese hito.
- [ ] Establecer pruebas de carga y criterios para habilitar polling; diferenciar consultas periódicas de fondo y polling de primer plano.

**Aceptación:** usuarios legítimos de una misma red pueden operar dentro de la política acordada; el bloqueo informa cuándo reintentar y los controles de abuso se conservan. **Consumidores:** M-11, M-13 y W-03.

### B-13 — Documentar y probar rotación CSRF · Nota CSRF · P1 de integración

- [ ] Documentar cuándo rota el token y cómo obtiene cada cliente el valor nuevo.
- [ ] Verificar escrituras secuenciales y concurrentes, incluyendo formularios en pestañas distintas.
- [ ] Acordar el tratamiento de tokens previos durante concurrencia si esta forma parte del uso permitido; mantener la protección CSRF.
- [ ] Distinguir fallos CSRF de otros 403 cuando sea posible y definir recuperación sin repetir escrituras no idempotentes.

**Aceptación:** Android y web consumen la rotación correctamente y no duplican operaciones al recuperarse. **Consumidores:** M-12.3 y W-03.

### B-14 — Preparar datos QA y cerrar cobertura de negocio · No probado · P1/P3

- [ ] Preparar combos, packs y promociones únicamente con autorización y con inventario de datos QA y limpieza prevista.
- [ ] Crear pedidos reproducibles con franjas relativas al momento de prueba para contra entrega, código validado y pago pendiente.
- [ ] Cubrir reembolsos desde incidencias, desistimiento y dar por perdido; comprobar dinero, estado, stock, historial y efectos únicos ante reintentos.
- [ ] Resolver el seguimiento del pedido 17 según su estado real cuando se ejecute la prueba; no asumir que continúa pendiente.
- [ ] Definir con los responsables si el alcance de notificaciones es polling actual o una futura implementación push; no crear infraestructura push por inferencia del informe.
- [ ] Revalidar IDOR, permisos por rol, CSRF, idempotencia, totales del servidor, restauración de stock y reembolsos que ya funcionaban.

**Aceptación:** cada escenario tiene datos, resultado esperado y evidencia; lo bloqueado por M3 o falta de autorización permanece explícitamente pendiente. **Consumidores:** M-13 y W-06.

## 2. Frontend web — Tienda, pago por link y panel administrativo

La web solo se probó en la página de pago por link. Los puntos siguientes son correcciones donde hay evidencia y tareas de verificación donde todavía no existe. No se asume que el panel web comparte los mismos defectos visuales de Android.

### W-01 — Completar integración y pruebas de 3DS · F-01 / No probado · P1

- [ ] Revisar la integración del SDK de Culqi y el intercambio de resultados con backend conforme al contrato validado en B-06.
- [ ] Cubrir flujo sin desafío, con desafío, cancelación, timeout y error de autenticación en sandbox.
- [ ] Mantener estados explícitos de procesamiento, autenticación, fallo y resultado incierto; bloquear envíos duplicados.
- [ ] Tras una interrupción, consultar el estado autorizado del intento antes de ofrecer repetir el cobro.
- [ ] Verificar regreso desde el desafío, recarga y navegación atrás sin crear otro pedido o cargo.

**Aceptación:** ningún estado de éxito se deduce solo de datos del navegador y los recorridos incompletos permiten una recuperación segura. **Dependencia:** B-06 y contrato de checkout.

### W-02 — Alinear checkout, pagos y mensajes · F-05 / F-06 / F-08 / F-09 · P1/P2

- [ ] Consumir el contrato acordado en B-08: `productId`, `paperBagFee`, respuesta de pago ausente y error de revalidación/stock.
- [ ] Manejar 204 sin intentar interpretar un cuerpo JSON; si se elige otra respuesta canónica, adaptar el cliente a ella.
- [ ] Mostrar el cargo de bolsa y los totales del servidor sin reconstruir reglas de precio de manera independiente.
- [ ] Ante cambios de stock o importe, mostrar la revalidación y pedir la confirmación que corresponda antes de continuar.
- [ ] Enviar solo los campos admitidos y preservar la idempotencia del intento según el contrato.
- [ ] Mostrar los motivos seguros de rechazo definidos en B-10 y una alternativa genérica cuando el motivo sea desconocido.
- [ ] Eliminar dependencias innecesarias de `providerOrderId` según B-11.
- [ ] Repetir los cinco rechazos de Culqi del informe y el pago por link; comprobar mensajes, estado y ausencia de pedidos para pagos rechazados.

**Aceptación:** interfaz y API coinciden en importes, errores y estados; el cliente entiende cómo continuar tras un rechazo. **Dependencias:** B-08, B-10, B-11.

### W-03 — Adaptar sesión, CSRF y límites · F-02 / F-03 / F-04 / F-10 / Nota CSRF · P1

- [ ] Leer el token CSRF vigente antes de cada escritura y recoger la rotación entre acciones; revisar el cliente HTTP compartido.
- [ ] Probar dos escrituras sucesivas, pestañas concurrentes, expiración y renovación de sesión.
- [ ] No repetir automáticamente pagos o escrituras no idempotentes ante 403, timeout o pérdida de respuesta.
- [ ] Diferenciar 401, 403, 405, 415, 429 y 500 mediante mensajes y acciones apropiados; verificar textos UTF-8.
- [ ] Mostrar la espera de `Retry-After` y deshabilitar reintentos mientras corresponda, contemplando cabecera ausente.
- [ ] Probar login/logout, pago por link y retorno de 3DS con la política definitiva de cookies.
- [ ] Integrar CAPTCHA solo si B-12 lo decide y publica el contrato; prever accesibilidad y errores del desafío.

**Aceptación:** las escrituras legítimas no fallan por reutilizar tokens antiguos y los bloqueos no provocan tormentas de reintentos. **Dependencias:** B-07, B-12, B-13.

### W-04 — Verificar renderizado de texto libre · F-07 · P1 de verificación

- [ ] Inventariar nombres, direcciones, eventos, notas e incidencias en tienda, historial, panel y vistas de confirmación.
- [ ] Revisar puntos donde se inserte HTML sin escape o se construyan URLs con datos del usuario.
- [ ] Renderizar como texto plano por defecto; si existe contenido enriquecido autorizado, aplicar una política específica junto con B-09.
- [ ] Probar entradas con etiquetas, atributos de evento, comillas y enlaces de esquema no permitido en los contextos donde se utilicen.
- [ ] Verificar persistencia y visualización por otro rol, para cubrir posibles fallos de XSS almacenado.

**Aceptación:** las entradas de prueba no ejecutan código ni generan enlaces peligrosos. Si no se reproduce un fallo, cerrar como verificación, no como corrección de una vulnerabilidad confirmada.

### W-05 — Verificar paridad operativa del panel web · APP-02 a APP-10, como referencia · P2

- [ ] Revisar que las líneas de compra distingan subtotal y precio unitario, sin asumir que reproducen APP-02.
- [ ] Mostrar asignación actual e historial real conforme a B-03.
- [ ] Verificar respuesta ante notas vacías, confirmación de proveedor y validación consistente con B-04.
- [ ] Comprobar limpieza de formularios y errores después de código validado, incidencia o cambio de estado.
- [ ] Aplicar la regla temporal de B-02 a los indicadores y acciones equivalentes que existan en la web.
- [ ] Revisar captura y presentación de direcciones conforme a B-05.
- [ ] Registrar resultados por pantalla y abrir correcciones solo para discrepancias reproducidas.

**Aceptación:** el panel expresa los mismos contratos operativos que la app, sin exigir replicar su diseño visual. **Dependencias:** B-02 a B-05.

### W-06 — Completar E2E de tienda y administración · No probado · P1

- [ ] Tienda: catálogo, combos/packs/promociones autorizados, carrito, stock, cargos, checkout, rechazos, 3DS y consulta de pedidos.
- [ ] Pago por link: franja no iniciada, cobro disponible, rechazo, aprobación, enlace repetido y pedido ya pagado.
- [ ] Panel: roles y permisos, colas, asignaciones, notas, incidencias, reprogramación, desistimiento, pérdida y reembolso.
- [ ] Verificar recarga, navegación atrás, doble clic, red interrumpida y múltiples pestañas en operaciones relevantes.
- [ ] Contrastar pedido, pago, stock e historial con backend, sin limitar la prueba al mensaje de éxito.
- [ ] Registrar navegador, versión, datos y evidencia por escenario; mantener identificadas las rutas no probadas.

**Aceptación:** existe una matriz ejecutada para tienda y panel; la prueba previa del link no se utiliza como evidencia de cobertura total. **Dependencias:** B-14 y tareas de contrato correspondientes.

## 3. Coordinación y secuencia

1. **Resolver contratos que condicionan Android:** B-01/B-02 y aclaraciones de B-03/B-04/B-05; comunicar decisiones al responsable móvil.
2. **Investigar pagos y corregir semántica API:** B-06/B-07/B-08, con W-01/W-02 según contratos disponibles.
3. **Cerrar integración de seguridad y sesión:** B-09 a B-13, W-03/W-04; mantener evidencia distinta para riesgos confirmados e hipótesis.
4. **Verificar operación web:** W-05 y casos de B-14 que soportan M-13.
5. **Ejecutar regresión cruzada:** W-06, M-13 y comprobaciones de backend. Habilitar polling únicamente tras el cierre documentado de su dependencia M3.

Cada entrega de backend debe incluir comportamiento acordado, ejemplos de respuestas, errores, compatibilidad y pruebas para que el trabajo móvil pueda avanzar sin adivinar reglas.

## 4. Trazabilidad del informe

| Hallazgo o pendiente | Trabajo responsable |
|---|---|
| APP-01 | Android M-01. |
| APP-02 | Android M-03; comprobación web W-05. |
| APP-03 | Android M-04, backend B-03, web W-05. |
| APP-04 / APP-07 | Android M-05/M-06, backend B-04, web W-05. |
| APP-05 | Android M-02, definición B-02, comprobación W-05. |
| APP-06 / APP-08 | Android M-07/M-08, contrato B-02 donde corresponda, web W-05. |
| APP-09 | Android M-09, backend B-05, web W-05. |
| APP-10 | Decisión B-02, Android M-10, web W-05. |
| SEC-01 | Backend B-01, integración Android M-11. |
| SEC-03 / SEC-04 | Android M-12. |
| F-01 | Backend B-06, web W-01. |
| F-02 / F-03 / F-04 | Backend B-07, web W-03, regresión Android M-12 según efecto. |
| F-05 / F-06 | Backend B-08, web W-02. |
| F-07 | Backend B-09, web W-04. |
| F-08 | Backend B-10, web W-02. |
| F-09 | Backend B-11, web W-02. |
| F-10 | Backend B-12, web W-03, Android M-11/M-13. |
| Nota CSRF | Backend B-13, web W-03, Android M-12.3. |
| Combos, packs, promociones y pagos/incidencias pendientes | B-14, W-06, M-13. |
| 3DS sin desafío, timeout y error | B-06, W-01. |
| Notificaciones y polling/M3 | B-12/B-14 y M-13; aclarar mecanismo real antes de ampliar alcance. |
| Panel administrativo y tienda sin probar | W-06. |
| Pedido 17 | B-14 y M-13, según estado vigente. |

**Criterio global:** cada punto termina como corregido y verificado, verificado sin reproducción, decisión de negocio documentada o pendiente con dependencia concreta. Las observaciones de sandbox, la falta de pruebas y las discrepancias con el código actual no se cierran como defectos corregidos sin evidencia.
