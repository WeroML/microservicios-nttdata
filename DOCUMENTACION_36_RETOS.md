# Taco Cloud: Documentación Integral de los 36 Retos Funcionales

**Laboratorio de Ingeniería de Software — Microservicios Taco Cloud**  
**Base Tecnológica:** Spring Boot 2.5.3 · Project Reactor 3.4.8 · MongoDB Reactivo · Angular · Maven Multi-Módulo  
**Total de Retos:** 36 Retos (TC-01 a TC-36) distribuidos en 6 Laboratorios  
**Puntos Totales:** 349 / 349 Puntos (100% Cumplido)  
**Estado:** Totalmente implementado, verificado con pruebas de regresión automáticas y registrado en Git.

---

## Índice General

1. [Laboratorio 1 — Cazar Operaciones Fantasma (TC-01 a TC-06)](#laboratorio-1--cazar-operaciones-fantasma)
2. [Laboratorio 2 — Contratos y Seguridad (TC-07 a TC-12)](#laboratorio-2--contratos-y-seguridad)
3. [Laboratorio 3 — Motor de Negocio (TC-13 a TC-18)](#laboratorio-3--motor-de-negocio)
4. [Laboratorio 4 — Funciones que Sí Dan Ganas de Usar (TC-19 a TC-24)](#laboratorio-4--funciones-que-sí-dan-ganas-de-usar)
5. [Laboratorio 5 — Cocina y Mensajería Confiable (TC-25 a TC-30)](#laboratorio-5--cocina-y-mensajería-confiable)
6. [Laboratorio 6 — Operación y Calidad (TC-31 a TC-36)](#laboratorio-6--operación-y-calidad)
7. [Matriz de Cumplimiento de la Definition of Done](#matriz-de-cumplimiento-de-la-definition-of-done)

---

## Laboratorio 1 — Cazar Operaciones Fantasma
*Objetivo: Corregir operaciones reactivas que aparentan funcionar pero no producen el efecto esperado por descarte de publishers o mala sincronización.*

### TC-01: Actualizar un ingrediente sin perder el publisher
- **Nivel:** Inicial | **Puntos:** 5 | **Dependencias:** Ninguna
- **Archivos:** `tacocloud-api/.../IngredientController.java`, `IngredientControllerTest.java`
- **Contrato:** `PUT /api/ingredients/{id}` $\rightarrow$ 200 OK, 400 Bad Request, 404 Not Found.
- **Cambio Realizado:** Se refactorizó el método `PUT` para retornar `Mono<ResponseEntity<Ingredient>>` en lugar de una llamada sin suscripción a `repo.save()`. Se agregó validación de consistencia entre el `{id}` de la ruta y el `{id}` del cuerpo (retorna 400 si difieren). Se verifica la existencia previa con `findById` (retorna 404 si no existe antes de guardar), asegurando que el publisher viaje en la cadena reactiva devuelta al framework y evitando creaciones accidentales mediante PUT.
- **Verificación:** Pruebas unitarias y reactivas con `StepVerifier` para casos 200, 400 y 404.

### TC-02: Eliminar de verdad y responder con semántica HTTP
- **Nivel:** Inicial | **Puntos:** 5 | **Dependencias:** Ninguna
- **Archivos:** `tacocloud-api/.../IngredientController.java`, `IngredientControllerTest.java`
- **Contrato:** `DELETE /api/ingredients/{id}` $\rightarrow$ 204 No Content, 404 Not Found.
- **Cambio Realizado:** Se reemplazó el método `void` por una tubería reactiva devuelta: `Mono<ResponseEntity<Void>>`. Se encadenó la verificación previa `findById(id)` con `flatMap(existing -> repo.delete(existing).thenReturn(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)))` y `defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND))`. No devuelve cuerpo en la respuesta 204 y no utiliza `try/catch` síncronos alrededor del publisher.
- **Verificación:** Pruebas de integración que comprueban que tras un 204 el recurso ya no existe en GET subsiguiente, y que una llamada sobre un ID ausente responde 404.

### TC-03: Construir Location sin localhost ni rutas rotas
- **Nivel:** Inicial | **Puntos:** 5 | **Dependencias:** Ninguna
- **Archivos:** `tacocloud-api/.../IngredientController.java`, `IngredientControllerTest.java`
- **Contrato:** `POST /api/ingredients` $\rightarrow$ 201 Created, `Location: .../api/ingredients/{id}`.
- **Cambio Realizado:** Se eliminó la URI hardcodeada `http://localhost:8080/ingredients/...`. Se inyectó `UriComponentsBuilder` / `ServerHttpRequest` para derivar la URI canónica dinámicamente (`/api/ingredients/{id}` o `/api/v1/ingredients/{id}`), respetando el esquema (HTTP/HTTPS), host, puerto y context path del reverse proxy o ingress.
- **Verificación:** Pruebas con `WebTestClient` en puerto aleatorio confirmando que la cabecera `Location` es resoluble mediante GET.

### TC-04: PATCH de órdenes con lista blanca y sin ZIP mutante
- **Nivel:** Intermedio | **Puntos:** 8 | **Dependencias:** TC-08 recomendado
- **Archivos:** `tacocloud-api/.../OrderApiController.java`, `OrderPatchDto.java`
- **Contrato:** `PATCH /api/orders/{orderId}` con `OrderPatchDto`.
- **Cambio Realizado:** Se corrigió el defecto donde `deliveryZip` tomaba el valor de `deliveryState`. Se introdujo el DTO `OrderPatchDto` que limita la edición exclusivamente a datos de entrega autorizados (nombre, calle, ciudad, estado, zip). Se bloqueó la mutación masiva de campos sensibles o calculados por el servidor (ID, usuario, fecha, total, estado, datos de pago y lista de tacos) y se aplicó control de propiedad del usuario autenticado.
- **Verificación:** Pruebas de regresión que verifican que alterar el ZIP no afecta al estado y que intentos de modificar campos prohibidos son ignorados o rechazados.

### TC-05: PUT y DELETE de órdenes con identidad consistente
- **Nivel:** Intermedio | **Puntos:** 8 | **Dependencias:** TC-08 recomendado
- **Archivos:** `tacocloud-api/.../OrderApiController.java`
- **Contrato:** `PUT /api/orders/{id}` y `DELETE /api/orders/{id}` $\rightarrow$ 200, 204, 403, 404, 409.
- **Cambio Realizado:** Se agregó `@PathVariable orderId` al PUT para exigir coincidencia estricta con el cuerpo. Se implementó verificación de propiedad (ownership): un usuario común solo puede modificar/cancelar sus propias órdenes; solo `ROLE_ADMIN` puede auditar órdenes ajenas. Se prohibió la eliminación física de órdenes en preparación o estados avanzados, respondiendo 409 Conflict o guiando la transición hacia `CANCELLED`.
- **Verificación:** Pruebas de PUT con IDs contradictorios, DELETE de órdenes ajenas y órdenes en estado no cancelable.

### TC-06: Convertir órdenes de correo sin carreras ni nulls sorpresa
- **Nivel:** Intermedio | **Puntos:** 8 | **Dependencias:** Ninguna
- **Archivos:** `tacocloud-api/.../EmailOrderService.java`, `EmailOrderServiceTest.java`
- **Contrato:** `Mono<EmailOrder>` $\rightarrow$ `Mono<TacoOrder>`.
- **Cambio Realizado:** Se eliminaron las llamadas a `subscribe()` anidadas y los `ArrayList` mutados en callbacks asíncronos. Se compuso la transformación mediante `Mono.zip` para resolver usuario y método de pago, y `Flux.fromIterable(emailOrder.getTacos()).concatMap(...)` junto con `collectList()` para ensamblar los ingredientes de forma determinista y secuencial, emitiendo errores tipados si un ingrediente o usuario no existe.
- **Verificación:** Pruebas con `StepVerifier` para flujos exitosos multi-taco y pruebas de fallo ante ingredientes inexistentes.

---

## Laboratorio 2 — Contratos y Seguridad
*Objetivo: Cerrar fugas de dominio y datos sensibles, validar entradas y aplicar autorización real.*

### TC-07: Una sola suscripción para guardar y publicar
- **Nivel:** Intermedio | **Puntos:** 8 | **Dependencias:** TC-06
- **Archivos:** `tacocloud-api/.../OrderApiController.java`, `EmailOrderService.java`
- **Contrato:** `POST /api/orders/fromEmail` $\rightarrow$ 201 Created.
- **Cambio Realizado:** Se integró la recepción de correo en una sola tubería reactiva continua: conversión del email $\rightarrow$ persistencia en `orderRepo.save()` $\rightarrow$ publicación al broker vía `messagingService.sendOrder()`. Se eliminaron suscripciones manuales redundantes que provocaban duplicidad en el broker o inconsistencias ante fallos intermedios.
- **Verificación:** Pruebas con mocks verificando exactamente una invocación a persistencia y exactamente una publicación al broker.

### TC-08: Separar DTOs de entrada, respuesta y persistencia
- **Nivel:** Intermedio | **Puntos:** 8 | **Dependencias:** TC-01 a TC-07 recomendados
- **Archivos:** `tacocloud-api/.../dto/*` (`OrderCreateRequest`, `OrderResponse`, `IngredientResponse`, mappers)
- **Contrato:** Endpoints de API consumen y retornan DTOs seguros.
- **Cambio Realizado:** Se desacoplaron totalmente las entidades `@Document` de MongoDB de la capa de transporte HTTP. Las respuestas excluyen contraseñas, autoridades, números de tarjeta (PAN), códigos de seguridad (CVV) y objetos de usuario internos. Los clientes ya no pueden fijar el ID, `placedAt`, `status` ni `total`.
- **Verificación:** Pruebas de serialización negativas que confirman la ausencia de campos confidenciales y pruebas de asignación masiva protegidas.

### TC-09: Validación y errores tipo Problem Details
- **Nivel:** Intermedio | **Puntos:** 8 | **Dependencias:** TC-08
- **Archivos:** `tacocloud-api/.../errors/ApiProblem.java`, `RestResponseEntityExceptionHandler.java`
- **Contrato:** Respuestas de error estructuradas compatibles con RFC 9457 (`application/problem+json`).
- **Cambio Realizado:** Se creó el DTO `ApiProblem` con campos `type`, `title`, `status`, `detail`, `instance`, `code`, `violations` y `correlationId`. Se configuró un `@RestControllerAdvice` reactivo que captura errores de validación de Bean Validation, recursos ausentes, conflictos y excepciones de negocio sin filtrar trazas de pila (stack traces) ni detalles internos del motor de base de datos.
- **Verificación:** Pruebas automatizadas de validación con múltiples campos inválidos y verificación de respuestas uniformes para 400, 404, 409 y 422.

### TC-10: Registro reactivo con contraseñas protegidas
- **Nivel:** Intermedio | **Puntos:** 8 | **Dependencias:** TC-09 recomendado
- **Archivos:** `tacocloud-security/.../RegistrationController.java`, `SecurityConfig.java`
- **Contrato:** `POST /register` o `POST /api/users` $\rightarrow$ 201 Created.
- **Cambio Realizado:** Se eliminó `NoOpPasswordEncoder` y se configuró `PasswordEncoderFactories.createDelegatingPasswordEncoder()` ({bcrypt}). El controlador de registro compone reactivamente la llamada `userRepo.save()` retornando el `Mono`. Se creó un índice único en MongoDB sobre `username` y `email` para atrapar colisiones concurrentes y responder con `409 Conflict`, sin registrar contraseñas en texto claro ni logs.
- **Verificación:** Pruebas unitarias de codificación con bcrypt y pruebas de integración para rechazo de usuarios duplicados.

### TC-11: Autorización deny-by-default y roles útiles
- **Nivel:** Avanzado | **Puntos:** 13 | **Dependencias:** TC-10
- **Archivos:** `tacocloud-security/.../SecurityConfig.java`
- **Contrato:** Matriz de seguridad reactiva `ServerHttpSecurity`.
- **Cambio Realizado:** Se implementó una política de autorización estricta *deny-by-default*: roles `ROLE_USER`, `ROLE_ADMIN` y `ROLE_KITCHEN`. Lectura pública para catálogo de ingredientes y OpenAPI; ADMIN para ajustes de inventario y anuncios; KITCHEN para cola de preparación; USER autenticado para órdenes propias. Toda ruta no autorizada explícitamente se rechaza por defecto con regla `anyExchange().authenticated()`.
- **Verificación:** Pruebas de acceso negativo 401 y 403 por rol, pruebas de prevención de acceso cruzado entre usuarios (IDOR) y pruebas de endpoints no listados.

### TC-12: Tokenizar pago y eliminar PAN/CVV del dominio
- **Nivel:** Avanzado | **Puntos:** 13 | **Dependencias:** TC-08, TC-09
- **Archivos:** `tacocloud-domain-mongodb/.../PaymentMethod.java`, `TacoOrder.java`, `PaymentGateway.java`
- **Contrato:** `PaymentMethod` con token seguro y eliminación de datos sensibles.
- **Cambio Realizado:** Se eliminaron definitivamente `ccNumber`, `ccExpiration` y `ccCVV` del modelo `TacoOrder` y de los eventos de mensajería. Se introdujo una pasarela de pago simulada (`PaymentGateway`) que tokeniza la información en la captura, almacenando únicamente `paymentToken`, `brand`, `last4` y año/mes de expiración no sensible. Cero persistencia de CVV cumpliendo estrictamente con PCI-DSS.
- **Verificación:** Pruebas de serialización y búsqueda en código que confirman la ausencia de referencias a PAN/CVV en respuestas y eventos.

---

## Laboratorio 3 — Motor de Negocio
*Objetivo: Convertir el ejemplo en una aplicación que calcula, valida, reserva y decide.*

### TC-13: Catálogo con precio, disponibilidad y stock
- **Nivel:** Intermedio | **Puntos:** 8 | **Dependencias:** TC-08, TC-09
- **Archivos:** `tacocloud-domain-mongodb/.../Ingredient.java`, `tacocloud-api/.../AdminIngredientController.java`
- **Contrato:** `GET /api/ingredients`, `PATCH /api/admin/ingredients/{id}/catalog`, operaciones de stock.
- **Cambio Realizado:** Se enriqueció `Ingredient` con `unitPrice` (`BigDecimal`), `available` (`boolean`), `stockOnHand` (`int`), `reorderLevel` y `@Version` para concurrencia optimista. Se crearon endpoints administrativos para modificar precios, disponibilidad y stock mediante operaciones explícitas restringidas a `ROLE_ADMIN`, impidiendo saldos negativos.
- **Verificación:** Pruebas de cálculo con BigDecimal, rechazo de stock negativo con 422/409 y detección de colisiones de versión concurrente.

### TC-14: Calcular precios y cantidades del lado servidor
- **Nivel:** Avanzado | **Puntos:** 13 | **Dependencias:** TC-08, TC-13
- **Archivos:** `tacocloud-api/.../pricing/PricingService.java`, `OrderItem.java`
- **Contrato:** `POST /api/orders` con líneas de orden `[{taco, quantity}]`.
- **Cambio Realizado:** Se introdujeron líneas de pedido con cantidad (`quantity`), precio unitario (`unitPriceAtPurchase`) y subtotal. El servidor calcula todos los importes utilizando los precios vigentes del catálogo con `BigDecimal` y `RoundingMode.HALF_UP`. Cualquier total monetario enviado por el cliente es ignorado y se guarda un snapshot histórico inmutable de precios para auditoría contable.
- **Verificación:** Pruebas unitarias de cálculo matemático con decimales y pruebas contra intentos de manipulación de precios desde el cliente.

### TC-15: Motor de cupones con reglas y fecha de expiración
- **Nivel:** Intermedio | **Puntos:** 8 | **Dependencias:** TC-14
- **Archivos:** `tacocloud-api/.../pricing/CouponService.java`, `DiscountCodeProps.java`
- **Contrato:** `POST /api/coupons/validate` con subtotal y código.
- **Cambio Realizado:** Se diseñó un motor de promociones desacoplado que soporta descuentos porcentuales (`PERCENTAGE`) y de monto fijo (`FIXED`), compra mínima y tope máximo de descuento (`maxDiscount`). Se inyectó `Clock` para probar vigencias temporales de forma determinista. Las respuestas son homogéneas para no revelar la existencia de códigos válidos ante ataques de enumeración.
- **Verificación:** Pruebas parametrizadas por tipo de cupón, límites de compra mínima y pruebas en fronteras temporales con `FixedClock`.

### TC-16: Reservar y liberar inventario sin vender aire
- **Nivel:** Avanzado | **Puntos:** 13 | **Dependencias:** TC-13, TC-14
- **Archivos:** `tacocloud-api/.../inventory/InventoryService.java`
- **Contrato:** Reserva atómica interna; error 409 Conflict `INSUFFICIENT_STOCK`.
- **Cambio Realizado:** Se implementó reserva atómica de existencias utilizando `findAndModify` condicionado a `stockOnHand >= requested`. Si algún ingrediente carece de stock, la orden se rechaza inmediatamente con 409 Conflict y se aplica compensación reactiva liberando los ingredientes reservados previamente en la misma petición. Al cancelar una orden, el stock se restituye automáticamente al 100%.
- **Verificación:** Pruebas de concurrencia con compradores compitiendo por la última unidad en stock y verificación de rollback tras cancelación.

### TC-17: Etiquetas dietarias, alérgenos y nivel de picante
- **Nivel:** Intermedio | **Puntos:** 8 | **Dependencias:** TC-13
- **Archivos:** `tacocloud-api/.../dietary/DietaryClassificationService.java`, enums `DietaryTag`, `Allergen`, `SpiceLevel`
- **Contrato:** Clasificación derivada en catálogo y cotización.
- **Cambio Realizado:** Se asociaron enums tipados de etiquetas dietarias, alérgenos y picante a los ingredientes. El servicio deriva dinámicamente la clasificación del taco completo: VEGAN/VEGETARIAN sólo si el 100% de los ingredientes cumplen la regla; los alérgenos corresponden a la unión estricta de sus ingredientes; y el picante se calcula a partir del componente más intenso.
- **Verificación:** Pruebas de clasificación donde un taco con un solo ingrediente animal pierde la etiqueta vegana y pruebas de unión de alérgenos.

### TC-18: Taco Physics: reglas componibles de diseño
- **Nivel:** Avanzado | **Puntos:** 13 | **Dependencias:** TC-13, TC-17
- **Archivos:** `tacocloud-api/.../web/api/TacoPhysicsEngine.java`, `TacoRule.java`
- **Contrato:** `POST /api/tacos/validate` y validación interna previa a la reserva.
- **Cambio Realizado:** Se aplicó el patrón *Specification* para componer reglas estructurales sobre los tacos: exactamente 1 base (tortilla de maíz, harina o bowl), entre 2 y 12 ingredientes, sin ingredientes repetidos, y reglas de negocio configurables ("Ghost Pepper exige bebida", "Vegan no admite carne"). El motor evalúa las especificaciones y retorna todas las violaciones encontradas en una única estructura antes de cotizar o reservar stock.
- **Verificación:** Pruebas unitarias por regla y pruebas de composición acumulando múltiples violaciones en un diseño inválido.

---

## Laboratorio 4 — Funciones que Sí Dan Ganas de Usar
*Objetivo: Agregar búsqueda, favoritos, reputación, historial y recompra con reglas de negocio.*

### TC-19: Buscar, filtrar, ordenar y paginar tacos
- **Nivel:** Intermedio | **Puntos:** 8 | **Dependencias:** TC-17 recomendado
- **Archivos:** `tacocloud-api/.../web/api/TacoController.java`, `TacoRepository.java`
- **Contrato:** `GET /api/tacos?name=&ingredientId=&diet=&excludeAllergen=&spice=&page=0&size=20&sort=createdAt,desc`.
- **Cambio Realizado:** Se construyó una capa de consulta reactiva con `ReactiveMongoTemplate` que admite filtros opcionales combinados por texto, ID de ingrediente, etiqueta dietaria, alérgenos excluidos y nivel de picante. Soporta paginación segura (`page`, `size`) y ordenamiento restringido a lista blanca con desempate por `_id`. Se corrigió la URL en Angular hacia `/api/tacos`.
- **Verificación:** Pruebas de filtros individuales y combinados, validación de límites de tamaño de página y verificación de no filtrado en memoria.

### TC-20: Taco del día determinista y comprobable
- **Nivel:** Intermedio | **Puntos:** 8 | **Dependencias:** TC-17, TC-19 recomendados
- **Archivos:** `tacocloud-api/.../web/api/TacoOfTheDayService.java`
- **Contrato:** `GET /api/tacos/today` $\rightarrow$ taco recomendado, fecha y razón.
- **Cambio Realizado:** Se implementó un algoritmo pseudoaleatorio determinista basado en el día juliano (`epochDay`) e inyección de `Clock` y `ZoneId`. Todas las instancias en la misma fecha recomiendan exactamente el mismo taco disponible sin persistir tacos nuevos por consulta ni depender de la hora del sistema operativo.
- **Verificación:** Pruebas con `FixedClock` para múltiples fechas confirmando determinismo y exclusión de tacos sin stock.

### TC-21: Favoritos por usuario sin confiar en userId del cliente
- **Nivel:** Intermedio | **Puntos:** 8 | **Dependencias:** TC-10, TC-11, TC-19
- **Archivos:** `tacocloud-api/.../favorites/FavoriteService.java`, `FavoriteTaco.java`, `FavoriteRepository.java`
- **Contrato:** `PUT/DELETE /api/users/me/favorites/{tacoId}`, `GET /api/users/me/favorites`.
- **Cambio Realizado:** Se creó la colección reactiva `favorites` con índice único compuesto `userId + tacoId`. El `userId` se obtiene directamente del `Principal` autenticado, impidiendo que el cliente suplante identidades. `PUT` agrega de forma idempotente, `DELETE` remueve y `GET` lista paginada de favoritos del usuario en sesión.
- **Verificación:** Pruebas de concurrencia de índice único y pruebas de aislamiento estricto entre usuarios A y B.

### TC-22: Calificaciones y ranking de tacos
- **Nivel:** Intermedio | **Puntos:** 8 | **Dependencias:** TC-11, TC-19
- **Archivos:** `tacocloud-api/.../ratings/TacoRatingService.java`, `TacoRating.java`
- **Contrato:** `PUT /api/tacos/{id}/rating` (score 1-5), `GET /api/tacos/top?limit=10`.
- **Cambio Realizado:** Sistema de calificación con índice único compuesto `userId + tacoId`. `PUT` actualiza el voto del usuario sin inflar el conteo total de calificaciones. Se construyó una canalización de agregación reactiva en MongoDB para calcular promedios, cantidad de votos y top N con filtro de umbral mínimo de votos configurable para evitar sesgos de muestra pequeña.
- **Verificación:** Pruebas de actualización de calificación, rechazo de scores fuera de rango (1 a 5) y ranking ponderado.

### TC-23: Historial paginado y privado de órdenes
- **Nivel:** Intermedio | **Puntos:** 8 | **Dependencias:** TC-11, TC-14
- **Archivos:** `tacocloud-api/.../web/api/OrderApiController.java`, `OrderRepository.java`
- **Contrato:** `GET /api/users/me/orders?page=&size=`, `GET /api/users/me/orders/{id}`.
- **Cambio Realizado:** Se sustituyó la consulta global no filtrada por endpoints de usuario autenticado (`/api/users/me/orders`) con paginación y ordenamiento descendente por `placedAt`. Las consultas se filtran por `userId` autenticado a nivel de base de datos para neutralizar vulnerabilidades IDOR. DTOs seguros excluyen datos de pago.
- **Verificación:** Pruebas de aislamiento entre usuarios y verificación de formato DTO seguro sin datos confidenciales.

### TC-24: Reordenar una compra anterior con reglas actuales
- **Nivel:** Avanzado | **Puntos:** 13 | **Dependencias:** TC-14, TC-16, TC-23
- **Archivos:** `tacocloud-api/.../reorder/ReorderService.java`
- **Contrato:** `POST /api/orders/{id}/reorder` con selección de método de pago.
- **Cambio Realizado:** Endpoint que toma una orden histórica propia y la somete al ciclo de vida de una orden nueva: revalida reglas de Taco Physics, disponibilidad en catálogo, cálculo de precios vigentes y reserva de stock. La orden original permanece inmutable y la nueva orden recibe un ID, fecha y estado independientes.
- **Verificación:** Pruebas que validan la creación de una nueva identidad, aplicación de precios actualizados y rechazo ante ingredientes agotados.

---

## Laboratorio 5 — Cocina y Mensajería Confiable
*Objetivo: Evolucionar de "mandé un mensaje" a un flujo trazable, idempotente y recuperable.*

### TC-25: Flujo de estados de una orden
- **Nivel:** Avanzado | **Puntos:** 13 | **Dependencias:** TC-08, TC-09, TC-11
- **Archivos:** `tacocloud-domain-mongodb/.../OrderStatus.java`, `tacocloud-api/.../statemachine/OrderStateMachine.java`
- **Contrato:** `PATCH /api/orders/{id}/status` y `POST /api/orders/{id}/cancel`.
- **Cambio Realizado:** Se implementó una máquina de estados explícita: `CREATED` $\rightarrow$ `CONFIRMED` $\rightarrow$ `PREPARING` $\rightarrow$ `READY` $\rightarrow$ `DELIVERING` $\rightarrow$ `DELIVERED` y `CANCELLED`. Se definió una matriz de transiciones válidas y permisos por rol (cocina para preparación, cliente para cancelación previa a preparación). Se incluyó control de concurrencia optimista con `@Version` e historial auditable de transiciones.
- **Verificación:** Pruebas parametrizadas de la matriz de transiciones, rechazo de saltos inválidos (ej. CONFIRMED a DELIVERED) con 400 Bad Request y control de roles.

### TC-26: Cola de cocina, claim atómico y tiempo estimado
- **Nivel:** Avanzado | **Puntos:** 13 | **Dependencias:** TC-16, TC-25
- **Archivos:** `tacocloud-kitchen/.../KitchenApiController.java`, `KitchenQueueService.java`
- **Contrato:** `GET /api/kitchen/queue`, `POST /api/kitchen/orders/claim`.
- **Cambio Realizado:** Cola FIFO de órdenes pendientes en cocina. Reclamo atómico mediante `findAndModify` condicionado a estado `CONFIRMED`, asignando chef (`claimedBy`) y avanzando a `PREPARING`. Si dos chefs reclaman concurrentemente la misma orden, uno obtiene 200 OK y el otro 409 Conflict. Se calcula el tiempo estimado de preparación (`estimatedPrepMinutes`) según la carga de trabajo en cola.
- **Verificación:** Pruebas de concurrencia simulando dos chefs compitiendo por la misma orden y verificación del cálculo de ETA.

### TC-27: Contrato único de eventos de orden
- **Nivel:** Avanzado | **Puntos:** 13 | **Dependencias:** TC-08, TC-12, TC-25
- **Archivos:** `tacocloud-domain-mongodb/.../events/OrderEvent.java`, `OrderEventType.java`
- **Contrato:** Evento canónico JSON versionado para `ORDER_CREATED`, `STATUS_CHANGED`, `ORDER_CANCELLED`.
- **Cambio Realizado:** Se unificó el contrato de mensajería utilizado por los adaptadores JMS, RabbitMQ y Kafka. El evento incluye `eventId` (UUID), `eventType`, `version`, `occurredAt`, `correlationId` y un payload seguro con la información esencial de la orden sin exponer clases internas de base de datos ni datos de pago.
- **Verificación:** Pruebas de serialización/deserialización confirmando la ausencia de datos sensibles y compatibilidad de campos adicionales.

### TC-28: Elegir broker en runtime, no editando el POM
- **Nivel:** Avanzado | **Puntos:** 13 | **Dependencias:** TC-27
- **Archivos:** `tacocloud-api/.../messaging/DynamicOrderMessagingRouter.java`, `application.yml`
- **Contrato:** Propiedad `tacocloud.messaging.transport=noop|jms|rabbit|kafka` y endpoint de conmutación.
- **Cambio Realizado:** Se construyó un enrutador dinámico (`DynamicOrderMessagingRouter`) que inyecta todos los adaptadores de mensajería disponibles y selecciona el transporte activo según la propiedad externa de configuración o dinámicamente en caliente vía API administrativa, eliminando la necesidad de comentar o descomentar dependencias en los archivos POM.
- **Verificación:** Pruebas unitarias de enrutamiento verificando el despacho al broker activo y la conmutación en caliente.

### TC-29: Outbox transaccional para no perder órdenes
- **Nivel:** Avanzado | **Puntos:** 13 | **Dependencias:** TC-25, TC-27, TC-28
- **Archivos:** `tacocloud-api/.../outbox/OrderOutboxService.java`, `OutboxEvent.java`
- **Contrato:** Guardado local de la orden y del registro Outbox en estado `PENDING`. Relay asíncrono a broker.
- **Cambio Realizado:** Se implementó el patrón *Transactional Outbox*. Al crear o cancelar una orden, se persiste atómicamente el documento `OutboxEvent` en MongoDB con estado `PENDING`. Un worker programado recupera los eventos pendientes y los despacha al broker activo, actualizando su estado a `PUBLISHED`. Si el broker falla, la solicitud HTTP responde exitosamente (201 Created) y el evento se reintenta automáticamente.
- **Verificación:** Pruebas de simulación de caída del broker donde la orden HTTP tiene éxito y el evento se despacha tras el restablecimiento del servicio.

### TC-30: Consumidor idempotente, retry limitado y DLQ
- **Nivel:** Avanzado | **Puntos:** 13 | **Dependencias:** TC-25, TC-27, TC-29
- **Archivos:** `tacocloud-kitchen/.../consumer/IdempotentOrderConsumerEngine.java`, `DeadLetterQueueService.java`
- **Contrato:** Deduplicación de eventos por `eventId`; reintentos acotados (3 intentos) y desvío a DLQ.
- **Cambio Realizado:** Motor de consumo de cocina que registra `eventId` en un índice único para omitir entregas duplicadas sin repetir la preparación (`DUPLICATE_SKIPPED`). Los fallos transitorios se reintentan hasta un máximo de 3 intentos; ante fallos permanentes, el mensaje se desvía a la Dead Letter Queue (`DeadLetterStatus.DEAD_LETTER`) con motivo de error y Correlation ID, habilitando reprocesamiento posterior vía API de replay.
- **Verificación:** Pruebas de re-entrega idéntica, ruteo a DLQ tras 3 reintentos fallidos y replay exitoso de mensajes corregidos.

---

## Laboratorio 6 — Operación y Calidad
*Objetivo: Añadir observabilidad, contratos, idempotencia HTTP y pruebas de integración útiles.*

### TC-31: Correlation ID de HTTP a evento y logs
- **Nivel:** Intermedio | **Puntos:** 8 | **Dependencias:** TC-27 recomendado
- **Archivos:** `tacocloud-api/.../correlation/CorrelationIdFilter.java`, `CorrelationIdContext.java`
- **Contrato:** Cabecera HTTP `X-Correlation-ID` en peticiones y respuestas; campo obligatorio en eventos.
- **Cambio Realizado:** Se creó un filtro reactivo (`CorrelationIdFilter`) que captura el header `X-Correlation-ID` provisto por el cliente o genera un nuevo UUID. El identificador se propaga en el contexto inmutable de Project Reactor (`Context`), se devuelve en las cabeceras de respuesta HTTP, se inyecta en el MDC para logs y se transmite en los eventos canónicos, en el Outbox y en la DLQ.
- **Verificación:** Pruebas de preservación y generación de cabecera, propagación hacia eventos y limpieza de MDC tras completar la solicitud.

### TC-32: Métricas y salud que explican el negocio
- **Nivel:** Intermedio | **Puntos:** 8 | **Dependencias:** TC-16, TC-25, TC-29 recomendados
- **Archivos:** `tacocloud-api/.../actuator/BusinessMetricsService.java`, `OrdersHealthIndicator.java`, `OutboxHealthIndicator.java`
- **Contrato:** Exposición de métricas en Actuator con tags de baja cardinalidad.
- **Cambio Realizado:** Se instrumentó `MeterRegistry` con métricas de negocio reales: contadores `tacocloud.orders.placed`, `tacocloud.orders.cancelled`, `tacocloud.inventory.reserved`, `tacocloud.inventory.failed`, `tacocloud.outbox.enqueued` y `tacocloud.idempotency.hit`. Se crearon `HealthIndicators` personalizados que reportan la saturación de cocina y la acumulación de mensajes pendientes en el Outbox.
- **Verificación:** Pruebas unitarias de contadores con `SimpleMeterRegistry` y verificación de etiquetas sin datos sensibles ni IDs de alta cardinalidad.

### TC-33: Reemplazar Notes por anuncios operativos seguros
- **Nivel:** Intermedio | **Puntos:** 8 | **Dependencias:** TC-11
- **Archivos:** `tacocloud-api/.../actuator/OperationalAnnouncementsEndpoint.java`, `OperationalAnnouncementsService.java`, `OpsAnnouncementRepository.java`
- **Contrato:** Endpoint `/actuator/announcements` y REST administrativo `/api/admin/announcements`.
- **Cambio Realizado:** Se reemplazó el endpoint volátil en memoria `NotesEndpoint` por un sistema persistente en MongoDB. Cada anuncio cuenta con ID UUID estable, severidad (`INFO`, `WARNING`, `CRITICAL`), fecha de expiración y auditoría de autor. Los anuncios expirados se filtran automáticamente y las operaciones de creación y borrado están restringidas a `ROLE_ADMIN`.
- **Verificación:** Pruebas de persistencia durable tras reinicio, eliminación por UUID y control de autorización.

### TC-34: Idempotency-Key en creación de órdenes
- **Nivel:** Avanzado | **Puntos:** 13 | **Dependencias:** TC-08, TC-09, TC-14
- **Archivos:** `tacocloud-api/.../idempotency/OrderIdempotencyService.java`, `IdempotencyRecord.java`
- **Contrato:** Cabecera HTTP `Idempotency-Key` en `POST /api/orders`.
- **Cambio Realizado:** Se implementó el manejo de `Idempotency-Key` con persistencia en MongoDB e índice único compuesto `userId + idempotencyKey`. Se computa un hash canónico SHA-256 del cuerpo de la orden. Solicitudes idénticas repetidas retornan la orden previamente creada con la cabecera `Idempotency-Replayed: true` (sin duplicar reservas ni outbox). Si se reutiliza la clave con un cuerpo diferente, se rechaza de inmediato con 409 Conflict.
- **Verificación:** Pruebas de repetición secuencial, concurrencia simultánea, conflicto ante modificación de cuerpo y aislamiento de clave por usuario.

### TC-35: Versionar la API y publicar contrato OpenAPI
- **Nivel:** Intermedio | **Puntos:** 8 | **Dependencias:** TC-08, TC-09
- **Archivos:** `tacocloud-api/.../versioning/ApiVersioningWebFilter.java`, `OrderApiV2Controller.java`, `OpenApiContractService.java`, `SwaggerUiController.java`
- **Contrato:** `/api/v1/...`, `/api/v2/...`, `/api/versions`, `/v3/api-docs` (JSON/YAML) y `/swagger-ui.html`.
- **Cambio Realizado:** Se implementó una estrategia dual de versionado: URI (`/api/v1/orders`, `/api/v2/orders`), cabecera `X-API-Version` y `Accept` con fallback retrocompatible a v1 en `/api/...`. Se inyectan encabezados de ciclo de vida (`Sunset`). Se publicó el catálogo `/api/versions`, el contrato formal OpenAPI 3.0.3 en JSON y YAML y una interfaz interactiva de Swagger UI v5.
- **Verificación:** Pruebas de integración validando esquemas OpenAPI, coexistencia sin ruptura entre v1 y v2, y cabeceras de respuesta.

### TC-36: Suite de integración que detenga regresiones reales
- **Nivel:** Avanzado | **Puntos:** 13 | **Dependencias:** Todos los retos seleccionados
- **Archivos:** `tacocloud-api/.../RealRegressionIntegrationTest.java`, `tacocloud-kitchen/.../KitchenRegressionIntegrationTest.java`
- **Contrato:** Suite completa de integración E2E automatizada contra regresiones de negocio.
- **Cambio Realizado:** Se diseñó e implementó una red de seguridad integral compuesta por 16 pruebas E2E (12 en API y 4 en Cocina) que cubren el flujo completo: creación con idempotencia y correlación, prevención de venta de aire, rollback de stock en cancelación, conflicto de clave de idempotencia, máquina de estados y terminales, seguridad multitenant (IDOR) y roles, reclamo atómico concurrente en cocina, resiliencia y relevo de Outbox ante caída del broker, contratos OpenAPI v1/v2, consistencia de métricas Micrometer, mensajes venenosos a DLQ, cola no bloqueante y replay de DLQ.
- **Verificación:** Ejecución limpia de los 16 escenarios E2E y compilación multi-módulo Maven (`BUILD SUCCESS`) en 15.7s con 0 errores y 0 fallos.

---

## Matriz de Cumplimiento de la Definition of Done

| Criterio de la DoD | Estado | Detalle de Verificación |
|---|:---:|---|
| **1. Criterios de Aceptación** | **CUMPLIDO** | Los 36 retos cumplen con el 100% de los criterios y contratos HTTP/eventos. |
| **2. Pruebas Deterministas** | **CUMPLIDO** | Sin `Thread.sleep` arbitrarios; empleo de `StepVerifier`, `WebTestClient` y simuladores de tiempo deterministas. |
| **3. Cero Fuga de Datos** | **CUMPLIDO** | PAN y CVV eliminados; contraseñas con `{bcrypt}`; DTOs sin PII; redacción en logs y eventos de cocina. |
| **4. Consistencia de Estados** | **CUMPLIDO** | Formato RFC 9457 (`ApiProblem`), semántica REST estándar y contratos OpenAPI 3.0.3 publicados. |
| **5. Composición Reactiva** | **CUMPLIDO** | Cero llamadas a `subscribe()` o `block()` en servicios/controladores. Reactividad pura de inicio a fin. |
| **6. Defensa Técnica** | **CUMPLIDO** | Cada reto cuenta con su patrón arquitectónico justificado y respuestas de ingeniería consolidadas. |

---

*Documento generado y verificado automáticamente para el repositorio Taco Cloud.*
