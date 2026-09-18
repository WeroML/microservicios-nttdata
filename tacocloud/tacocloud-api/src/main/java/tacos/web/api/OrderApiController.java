package tacos.web.api;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.User;
import tacos.data.IngredientRepository;
import tacos.data.OrderRepository;
import tacos.data.TacoRepository;
import tacos.data.UserRepository;
import javax.validation.Valid;

import tacos.TacoOrder.OrderStatus;
import tacos.events.OrderEvent;
import tacos.events.OrderEventType;
import tacos.messaging.OrderMessagingService;
import tacos.web.api.dto.ClaimOrderRequest;
import tacos.web.api.dto.KitchenQueueItem;
import tacos.web.api.dto.KitchenQueueResponse;
import tacos.web.api.dto.OrderEtaResponse;
import tacos.web.api.dto.OrderResponse;
import tacos.web.api.dto.OrderStatusResponse;
import tacos.web.api.dto.PagedResponse;
import tacos.web.api.dto.ReorderRequest;
import tacos.web.api.dto.UpdateOrderStatusRequest;

@RestController
@RequestMapping(path="/api/orders",
                produces="application/json")
@CrossOrigin(origins="http://localhost:8080")
public class OrderApiController {

  private OrderRepository repo;
  private OrderMessagingService orderMessages;
  private EmailOrderService emailOrderService;
  private IngredientRepository ingredientRepo;
  private TacoRepository tacoRepo;
  // Ejercicio 15: Motor de cupones con reglas y fecha de expiración
  private CouponEngine couponEngine;
  // Ejercicio 16: Reservar y liberar inventario sin vender aire
  private InventoryService inventoryService;
  // Ejercicio 18: Taco Physics: reglas componibles de diseño
  private TacoPhysicsEngine physicsEngine;
  // Ejercicio 23: Historial paginado y privado de órdenes
  private UserRepository userRepo;
  // Ejercicio 26: Cola de cocina, claim atómico y tiempo estimado
  private KitchenService kitchenService;
  // Ejercicio 29: Outbox transaccional para no perder órdenes
  private tacos.outbox.TransactionalOutboxService outboxService;

  public OrderApiController(OrderRepository repo,
                            OrderMessagingService orderMessages,
                            EmailOrderService emailOrderService) {
    this(repo, orderMessages, emailOrderService, null, null, null, null, null, null, null, null);
  }

  public OrderApiController(OrderRepository repo,
                            OrderMessagingService orderMessages,
                            EmailOrderService emailOrderService,
                            IngredientRepository ingredientRepo,
                            TacoRepository tacoRepo) {
    this(repo, orderMessages, emailOrderService, ingredientRepo, tacoRepo, null, null, null, null, null, null);
  }

  public OrderApiController(OrderRepository repo,
                            OrderMessagingService orderMessages,
                            EmailOrderService emailOrderService,
                            IngredientRepository ingredientRepo,
                            TacoRepository tacoRepo,
                            CouponEngine couponEngine) {
    this(repo, orderMessages, emailOrderService, ingredientRepo, tacoRepo, couponEngine, null, null, null, null, null);
  }

  public OrderApiController(OrderRepository repo,
                            OrderMessagingService orderMessages,
                            EmailOrderService emailOrderService,
                            IngredientRepository ingredientRepo,
                            TacoRepository tacoRepo,
                            CouponEngine couponEngine,
                            InventoryService inventoryService) {
    this(repo, orderMessages, emailOrderService, ingredientRepo, tacoRepo, couponEngine, inventoryService, null, null, null, null);
  }

  public OrderApiController(OrderRepository repo,
                            OrderMessagingService orderMessages,
                            EmailOrderService emailOrderService,
                            IngredientRepository ingredientRepo,
                            TacoRepository tacoRepo,
                            CouponEngine couponEngine,
                            InventoryService inventoryService,
                            TacoPhysicsEngine physicsEngine) {
    this(repo, orderMessages, emailOrderService, ingredientRepo, tacoRepo, couponEngine, inventoryService, physicsEngine, null, null, null);
  }

  public OrderApiController(OrderRepository repo,
                            OrderMessagingService orderMessages,
                            EmailOrderService emailOrderService,
                            IngredientRepository ingredientRepo,
                            TacoRepository tacoRepo,
                            CouponEngine couponEngine,
                            InventoryService inventoryService,
                            TacoPhysicsEngine physicsEngine,
                            UserRepository userRepo) {
    this(repo, orderMessages, emailOrderService, ingredientRepo, tacoRepo, couponEngine, inventoryService, physicsEngine, userRepo, null, null);
  }

  public OrderApiController(OrderRepository repo,
                            OrderMessagingService orderMessages,
                            EmailOrderService emailOrderService,
                            IngredientRepository ingredientRepo,
                            TacoRepository tacoRepo,
                            CouponEngine couponEngine,
                            InventoryService inventoryService,
                            TacoPhysicsEngine physicsEngine,
                            UserRepository userRepo,
                            KitchenService kitchenService) {
    this(repo, orderMessages, emailOrderService, ingredientRepo, tacoRepo, couponEngine, inventoryService, physicsEngine, userRepo, kitchenService, null);
  }

  @Autowired
  public OrderApiController(OrderRepository repo,
                            OrderMessagingService orderMessages,
                            EmailOrderService emailOrderService,
                            IngredientRepository ingredientRepo,
                            TacoRepository tacoRepo,
                            CouponEngine couponEngine,
                            InventoryService inventoryService,
                            TacoPhysicsEngine physicsEngine,
                            @Autowired(required = false) UserRepository userRepo,
                            @Autowired(required = false) KitchenService kitchenService,
                            @Autowired(required = false) tacos.outbox.TransactionalOutboxService outboxService) {
    this.repo = repo;
    this.orderMessages = orderMessages;
    this.emailOrderService = emailOrderService;
    this.ingredientRepo = ingredientRepo;
    this.tacoRepo = tacoRepo;
    this.couponEngine = couponEngine;
    this.inventoryService = inventoryService;
    this.physicsEngine = physicsEngine;
    this.userRepo = userRepo;
    this.kitchenService = kitchenService != null ? kitchenService : new KitchenService(repo);
    this.outboxService = outboxService;
  }

  public void setOutboxService(tacos.outbox.TransactionalOutboxService outboxService) {
    this.outboxService = outboxService;
  }

  // Ejercicio 23: Historial paginado y privado de órdenes
  @GetMapping(produces="application/json")
  public Mono<ResponseEntity<PagedResponse<TacoOrder>>> allOrders(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size,
      Principal principal) {
    return resolveAuthenticatedUser(principal)
        .flatMap(user -> repo.findAll()
            .filter(order -> isOrderOwnedByUser(order, user))
            .collectList()
            .map(userOrders -> {
              // Ordenar por fecha descendente (más recientes primero)
              userOrders.sort((o1, o2) -> {
                Date d1 = o1.getPlacedAt() != null ? o1.getPlacedAt() : new Date(0);
                Date d2 = o2.getPlacedAt() != null ? o2.getPlacedAt() : new Date(0);
                return d2.compareTo(d1);
              });

              PagedResponse<TacoOrder> paged = PagedResponse.of(userOrders, page, size);
              return ResponseEntity.ok()
                  .header("X-Total-Count", String.valueOf(paged.getTotalElements()))
                  .header("X-Total-Pages", String.valueOf(paged.getTotalPages()))
                  .header("X-Current-Page", String.valueOf(paged.getPage()))
                  .header("X-Page-Size", String.valueOf(paged.getSize()))
                  .body(paged);
            }));
  }

  // Ejercicio 23: Historial paginado y privado de órdenes
  @GetMapping(path = "/history", produces = "application/json")
  public Mono<ResponseEntity<PagedResponse<TacoOrder>>> orderHistory(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size,
      Principal principal) {
    return allOrders(page, size, principal);
  }

  // Ejercicio 23: Historial paginado y privado de órdenes
  @GetMapping(path = "/{orderId}", produces = "application/json")
  public Mono<TacoOrder> getOrderById(@PathVariable("orderId") String orderId, Principal principal) {
    return resolveAuthenticatedUser(principal)
        .flatMap(user -> repo.findById(orderId)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada: " + orderId)))
            .flatMap(order -> {
              if (!isOrderOwnedByUser(order, user)) {
                return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Acceso denegado: No está autorizado para consultar la orden de otro usuario."));
              }
              return Mono.just(order);
            }));
  }

  // Ejercicio 25: Flujo de estados de una orden
  // Ejercicio 8: Separar DTOs de entrada, respuesta y persistencia
  @GetMapping(path = "/{orderId}/details", produces = "application/json")
  public Mono<OrderResponse> getOrderDetails(@PathVariable("orderId") String orderId, Principal principal) {
    return resolveAuthenticatedUser(principal)
        .flatMap(user -> repo.findById(orderId)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada: " + orderId)))
            .flatMap(order -> {
              boolean admin = isUserAdmin(principal);
              if (!admin && !isOrderOwnedByUser(order, user)) {
                return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Acceso denegado: No está autorizado para consultar la orden de otro usuario."));
              }
              return Mono.just(OrderResponse.fromEntity(order));
            }));
  }

  // Ejercicio 25: Flujo de estados de una orden
  // Ejercicio 8: Separar DTOs de entrada, respuesta y persistencia
  @GetMapping(path = "/{orderId}/status", produces = "application/json")
  public Mono<OrderStatusResponse> getOrderStatus(@PathVariable("orderId") String orderId, Principal principal) {
    return resolveAuthenticatedUser(principal)
        .flatMap(user -> repo.findById(orderId)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada: " + orderId)))
            .flatMap(order -> {
              boolean admin = isUserAdmin(principal);
              if (!admin && !isOrderOwnedByUser(order, user)) {
                return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Acceso denegado: No está autorizado para consultar el estado de esta orden."));
              }
              OrderStatus current = order.getStatus() != null ? order.getStatus() : OrderStatus.CONFIRMED;
              return Mono.just(OrderStatusResponse.builder()
                  .orderId(order.getId())
                  .currentStatus(current)
                  .updatedAt(new Date())
                  .allowedNextStates(current.allowedNextStates())
                  .terminal(current.isTerminal())
                  .build());
            }));
  }

  // Ejercicio 25: Flujo de estados de una orden
  // Ejercicio 8: Separar DTOs de entrada, respuesta y persistencia
  // Ejercicio 9: Validación y errores tipo Problem Details
  // Ejercicio 11: Autorización deny-by-default y roles útiles
  @PatchMapping(path = "/{orderId}/status", consumes = "application/json", produces = "application/json")
  public Mono<OrderStatusResponse> updateOrderStatus(
      @PathVariable("orderId") String orderId,
      @Valid @RequestBody UpdateOrderStatusRequest request,
      Principal principal) {
    return resolveAuthenticatedUser(principal)
        .flatMap(user -> repo.findById(orderId)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada: " + orderId)))
            .flatMap(order -> {
              boolean admin = isUserAdmin(principal);
              OrderStatus current = order.getStatus() != null ? order.getStatus() : OrderStatus.CONFIRMED;
              OrderStatus target = request.getStatus();

              // Autorización Deny-By-Default y Roles Útiles (Ejercicio 11)
              if (!admin) {
                // Cliente común (ROLE_USER): solo puede gestionar sus propias órdenes (IDOR)
                if (!isOrderOwnedByUser(order, user)) {
                  return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                      "Acceso denegado: No está autorizado para modificar la orden de otro usuario."));
                }
                // Un cliente únicamente tiene permitido solicitar CANCELLED
                if (target != OrderStatus.CANCELLED) {
                  return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                      "Acceso denegado: Solo el personal de cocina o administradores (ROLE_ADMIN) pueden avanzar el estado operativo de la orden a " + target + "."));
                }
                // Cancelación permitida solo en etapas iniciales (PENDING o CONFIRMED)
                if (current != OrderStatus.PENDING && current != OrderStatus.CONFIRMED) {
                  return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                      "No es posible cancelar la orden: la orden ya se encuentra en proceso de " + current + ". Por favor contacte a soporte."));
                }
              }

              // Validación de la Máquina de Estados (RFC 7807 Problem Details - Ejercicio 9)
              if (!current.canTransitionTo(target)) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Transición de estado inválida: No es posible cambiar la orden de '" + current + "' a '" + target +
                    "'. Estados siguientes permitidos: " + current.allowedNextStates() + "."));
              }

              // Aplicar transición
              order.setStatus(target);

              // Liberar inventario si transiciona a CANCELLED
              Mono<Void> releaseInventoryMono = (target == OrderStatus.CANCELLED && inventoryService != null)
                  ? inventoryService.releaseInventory(order)
                  : Mono.empty();

              return releaseInventoryMono
                  .then(repo.save(order))
                  .flatMap(savedOrder -> {
                    // Ejercicio 27: Contrato único de eventos de orden
                    // Ejercicio 29: Outbox transaccional para no perder órdenes
                    // Ejercicio 31: Correlation ID de HTTP a evento y logs
                    return tacos.web.api.correlation.CorrelationIdSupport.getCorrelationId()
                        .flatMap(cid -> {
                          savedOrder.setCorrelationId(cid);
                          OrderEvent event = OrderEvent.fromOrder(savedOrder, OrderEventType.fromOrderStatus(savedOrder.getStatus()), OrderEvent.DEFAULT_SOURCE, cid);
                          Mono<?> dispatchMono = Mono.empty();
                          if (outboxService != null) {
                            dispatchMono = outboxService.enqueueEvent(event);
                          } else if (orderMessages != null) {
                            orderMessages.sendOrderEvent(event);
                          }

                          return dispatchMono.thenReturn(OrderStatusResponse.builder()
                              .orderId(savedOrder.getId())
                              .previousStatus(current)
                              .currentStatus(savedOrder.getStatus())
                              .reason(request.getReason())
                              .updatedAt(new Date())
                              .allowedNextStates(savedOrder.getStatus().allowedNextStates())
                              .terminal(savedOrder.getStatus().isTerminal())
                              .build());
                        });
                  });
            }));
  }

  // Ejercicio 26: Cola de cocina, claim atómico y tiempo estimado
  @GetMapping(path = "/queue", produces = "application/json")
  public Mono<KitchenQueueResponse> getQueue(Principal principal) {
    if (principal == null) {
      return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
    }
    if (!isUserAdmin(principal)) {
      return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
          "Acceso denegado: Solo personal de cocina o administradores (ROLE_ADMIN) pueden consultar la cola de cocina."));
    }
    return kitchenService.getKitchenQueue();
  }

  // Ejercicio 26: Cola de cocina, claim atómico y tiempo estimado
  @GetMapping(path = "/kitchen/queue", produces = "application/json")
  public Mono<KitchenQueueResponse> getKitchenQueue(Principal principal) {
    return getQueue(principal);
  }

  // Ejercicio 26: Cola de cocina, claim atómico y tiempo estimado
  @PostMapping(path = "/{orderId}/claim", produces = "application/json")
  public Mono<KitchenQueueItem> claimOrder(
      @PathVariable("orderId") String orderId,
      @RequestBody(required = false) ClaimOrderRequest request,
      Principal principal) {
    if (principal == null) {
      return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
    }
    if (!isUserAdmin(principal)) {
      return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
          "Acceso denegado: Solo personal de cocina o administradores (ROLE_ADMIN) pueden tomar órdenes."));
    }
    String chefId = principal.getName();
    return kitchenService.claimOrder(orderId, chefId, request);
  }

  // Ejercicio 26: Cola de cocina, claim atómico y tiempo estimado
  @PostMapping(path = "/{orderId}/unclaim", produces = "application/json")
  public Mono<KitchenQueueItem> unclaimOrder(
      @PathVariable("orderId") String orderId,
      Principal principal) {
    if (principal == null) {
      return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
    }
    if (!isUserAdmin(principal)) {
      return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
          "Acceso denegado: Solo personal de cocina o administradores (ROLE_ADMIN) pueden liberar órdenes."));
    }
    String chefId = principal.getName();
    boolean isAdmin = isUserAdmin(principal);
    return kitchenService.unclaimOrder(orderId, chefId, isAdmin);
  }

  // Ejercicio 26: Cola de cocina, claim atómico y tiempo estimado
  @GetMapping(path = "/{orderId}/eta", produces = "application/json")
  public Mono<OrderEtaResponse> getOrderEta(
      @PathVariable("orderId") String orderId,
      Principal principal) {
    if (principal == null) {
      return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
    }
    return resolveAuthenticatedUser(principal)
        .flatMap(user -> {
          boolean isAdmin = isUserAdmin(principal);
          return kitchenService.getOrderEta(orderId, user, isAdmin);
        });
  }

  // Ejercicio 24: Reordenar una compra anterior con reglas actuales
  // Ejercicio 31: Correlation ID de HTTP a evento y logs
  @PostMapping(path = "/{orderId}/reorder", produces = "application/json")
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<TacoOrder> reorderOrder(
      @PathVariable("orderId") String orderId,
      @RequestBody(required = false) ReorderRequest reorderRequest,
      Principal principal) {
    return tacos.web.api.correlation.CorrelationIdSupport.getCorrelationId()
        .flatMap(cid -> prepareReorder(orderId, reorderRequest, principal)
            .flatMap(newOrder -> {
              newOrder.setCorrelationId(cid);
              if (inventoryService != null) {
                return inventoryService.reserveInventory(newOrder);
              }
              return Mono.just(newOrder);
            })
            .flatMap(repo::save)
            .flatMap(savedOrder -> {
              savedOrder.setCorrelationId(cid);
              if (outboxService != null) {
                return outboxService.enqueueOrder(savedOrder, OrderEventType.ORDER_CREATED)
                    .thenReturn(savedOrder);
              } else if (orderMessages != null) {
                orderMessages.sendOrder(savedOrder);
                orderMessages.sendOrderEvent(OrderEvent.fromOrder(savedOrder, OrderEventType.ORDER_CREATED, OrderEvent.DEFAULT_SOURCE, cid));
                return Mono.just(savedOrder);
              }
              return Mono.just(savedOrder);
            }));
  }

  // Ejercicio 24: Reordenar una compra anterior con reglas actuales
  @GetMapping(path = "/{orderId}/reorder-preview", produces = "application/json")
  public Mono<TacoOrder> previewReorder(
      @PathVariable("orderId") String orderId,
      @RequestParam(name = "couponCode", required = false) String couponCode,
      @RequestParam(name = "dropExpiredCoupon", defaultValue = "false") boolean dropExpiredCoupon,
      Principal principal) {
    ReorderRequest req = new ReorderRequest();
    req.setCouponCode(couponCode);
    req.setDropExpiredCoupon(dropExpiredCoupon);
    return prepareReorder(orderId, req, principal);
  }

  // Ejercicio 24: Reordenar una compra anterior con reglas actuales
  private Mono<TacoOrder> prepareReorder(String orderId, ReorderRequest reorderRequest, Principal principal) {
    return resolveAuthenticatedUser(principal)
        .flatMap(user -> repo.findById(orderId)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden previa no encontrada: " + orderId)))
            .flatMap(originalOrder -> {
              if (!isOrderOwnedByUser(originalOrder, user)) {
                return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Acceso denegado: No está autorizado para reordenar la compra de otro usuario."));
              }

              if (originalOrder.getTacos() == null || originalOrder.getTacos().isEmpty()) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La orden previa no contiene tacos para reordenar."));
              }

              TacoOrder newOrder = new TacoOrder();
              newOrder.setUser(user);
              newOrder.setPlacedAt(new Date());
              newOrder.setStatus(TacoOrder.OrderStatus.CONFIRMED);

              // Delivery details
              if (reorderRequest != null && reorderRequest.getDeliveryName() != null && !reorderRequest.getDeliveryName().trim().isEmpty()) {
                newOrder.setDeliveryName(reorderRequest.getDeliveryName().trim());
              } else {
                newOrder.setDeliveryName(originalOrder.getDeliveryName());
              }

              if (reorderRequest != null && reorderRequest.getDeliveryStreet() != null && !reorderRequest.getDeliveryStreet().trim().isEmpty()) {
                newOrder.setDeliveryStreet(reorderRequest.getDeliveryStreet().trim());
              } else {
                newOrder.setDeliveryStreet(originalOrder.getDeliveryStreet());
              }

              if (reorderRequest != null && reorderRequest.getDeliveryCity() != null && !reorderRequest.getDeliveryCity().trim().isEmpty()) {
                newOrder.setDeliveryCity(reorderRequest.getDeliveryCity().trim());
              } else {
                newOrder.setDeliveryCity(originalOrder.getDeliveryCity());
              }

              if (reorderRequest != null && reorderRequest.getDeliveryState() != null && !reorderRequest.getDeliveryState().trim().isEmpty()) {
                newOrder.setDeliveryState(reorderRequest.getDeliveryState().trim());
              } else {
                newOrder.setDeliveryState(originalOrder.getDeliveryState());
              }

              if (reorderRequest != null && reorderRequest.getDeliveryZip() != null && !reorderRequest.getDeliveryZip().trim().isEmpty()) {
                newOrder.setDeliveryZip(reorderRequest.getDeliveryZip().trim());
              } else {
                newOrder.setDeliveryZip(originalOrder.getDeliveryZip());
              }

              // Payment details
              if (reorderRequest != null && reorderRequest.getPaymentToken() != null && !reorderRequest.getPaymentToken().trim().isEmpty()) {
                newOrder.setPaymentToken(reorderRequest.getPaymentToken().trim());
                newOrder.setCcExpiration(reorderRequest.getCcExpiration());
                newOrder.setLast4(reorderRequest.getLast4());
              } else {
                newOrder.setPaymentToken(originalOrder.getPaymentToken());
                newOrder.setCcExpiration(originalOrder.getCcExpiration());
                newOrder.setLast4(originalOrder.getLast4());
              }

              // Coupon resolution
              String effectiveCoupon = null;
              if (reorderRequest != null && reorderRequest.getCouponCode() != null) {
                String c = reorderRequest.getCouponCode().trim();
                effectiveCoupon = c.isEmpty() ? null : c;
              } else {
                effectiveCoupon = originalOrder.getCouponCode();
              }
              newOrder.setCouponCode(effectiveCoupon);

              // Clone tacos
              java.util.List<Taco> clonedTacos = new java.util.ArrayList<>();
              for (Taco ot : originalOrder.getTacos()) {
                Taco ct = new Taco();
                ct.setId(ot.getId());
                ct.setName(ot.getName());
                ct.setQuantity(ot.getQuantity() != null && ot.getQuantity() > 0 ? ot.getQuantity() : 1);
                if (ot.getIngredients() != null) {
                  clonedTacos.add(ct);
                  ct.setIngredients(new java.util.ArrayList<>(ot.getIngredients()));
                } else {
                  clonedTacos.add(ct);
                }
              }
              newOrder.setTacos(clonedTacos);

              return validateTacoPhysics(newOrder)
                  .flatMap(ord -> {
                    Mono<TacoOrder> pricing = calculateOrderPrices(ord);
                    if (reorderRequest != null && Boolean.TRUE.equals(reorderRequest.getDropExpiredCoupon())) {
                      pricing = pricing.onErrorResume(ResponseStatusException.class, ex -> {
                        if (ex.getStatus() == HttpStatus.BAD_REQUEST && ex.getReason() != null
                            && ex.getReason().toLowerCase().contains("expirado")) {
                          ord.setCouponCode(null);
                          ord.setDiscount(BigDecimal.ZERO);
                          return calculateOrderPrices(ord);
                        }
                        return Mono.error(ex);
                      });
                    }
                    return pricing;
                  });
            }));
  }

  // Ejercicio 14: Calcular precios y cantidades del lado servidor
  // Ejercicio 16: Reservar y liberar inventario sin vender aire
  // Ejercicio 18: Taco Physics: reglas componibles de diseño
  // Ejercicio 23: Historial paginado y privado de órdenes
  // Ejercicio 31: Correlation ID de HTTP a evento y logs
  @PostMapping(consumes="application/json")
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<TacoOrder> postOrder(@RequestBody TacoOrder order,
                                  @Autowired(required = false) Principal principal) {
    return tacos.web.api.correlation.CorrelationIdSupport.getCorrelationId()
        .flatMap(cid -> {
          if (order.getCorrelationId() == null) {
            order.setCorrelationId(cid);
          }

          Mono<TacoOrder> preparedOrder = (principal != null)
              ? resolveAuthenticatedUser(principal)
                  .map(user -> {
                    order.setUser(user);
                    return order;
                  })
                  .defaultIfEmpty(order)
              : Mono.just(order);

          return preparedOrder
              .flatMap(this::validateTacoPhysics)
              .flatMap(this::calculateOrderPrices)
              .flatMap(ord -> {
                if (inventoryService != null) {
                  return inventoryService.reserveInventory(ord);
                }
                return Mono.just(ord);
              })
              .flatMap(repo::save)
              .flatMap(savedOrder -> {
                if (savedOrder.getCorrelationId() == null) {
                  savedOrder.setCorrelationId(cid);
                }
                // Ejercicio 29: Outbox transaccional para no perder órdenes
                // Ejercicio 31: Correlation ID de HTTP a evento y logs
                if (outboxService != null) {
                  return outboxService.enqueueOrder(savedOrder, OrderEventType.ORDER_CREATED)
                      .thenReturn(savedOrder);
                } else if (orderMessages != null) {
                  orderMessages.sendOrder(savedOrder);
                  orderMessages.sendOrderEvent(OrderEvent.fromOrder(savedOrder, OrderEventType.ORDER_CREATED, OrderEvent.DEFAULT_SOURCE, cid));
                  return Mono.just(savedOrder);
                }
                return Mono.just(savedOrder);
              });
        });
  }

  // Ejercicio 18: Taco Physics: reglas componibles de diseño
  private Mono<TacoOrder> validateTacoPhysics(TacoOrder order) {
    if (physicsEngine == null || order == null || order.getTacos() == null || order.getTacos().isEmpty()) {
      return Mono.justOrEmpty(order);
    }
    return Flux.fromIterable(order.getTacos())
        .flatMap(physicsEngine::validateAndPass)
        .then(Mono.just(order));
  }

  // Ejercicio 14: Calcular precios y cantidades del lado servidor
  // Ejercicio 15: Motor de cupones con reglas y fecha de expiración
  public Mono<TacoOrder> calculateOrderPrices(TacoOrder order) {
    if (order.getTacos() == null || order.getTacos().isEmpty()) {
      order.setSubTotal(BigDecimal.ZERO);
      order.setTotal(BigDecimal.ZERO);
      if (couponEngine != null) {
        return couponEngine.applyCoupon(order, new java.util.Date());
      }
      return Mono.just(order);
    }

    return Flux.fromIterable(order.getTacos())
        .flatMap(taco -> {
          // Normalizar cantidad autoritativamente (mínimo 1)
          int qty = (taco.getQuantity() != null && taco.getQuantity() > 0) ? taco.getQuantity() : 1;
          taco.setQuantity(qty);

          // Si es un taco preconfigurado del catálogo
          if (taco.getId() != null && tacoRepo != null) {
            return tacoRepo.findById(taco.getId())
                .map(catalogTaco -> {
                  taco.setName(catalogTaco.getName());
                  taco.setPrice(catalogTaco.getPrice());
                  if (taco.getIngredients() == null || taco.getIngredients().isEmpty()) {
                    taco.setIngredients(catalogTaco.getIngredients());
                  }
                  return taco;
                })
                .switchIfEmpty(Mono.defer(() -> calculateCustomTacoPrice(taco)));
          }

          return calculateCustomTacoPrice(taco);
        })
        .collectList()
        .map(tacos -> {
          order.setTacos(tacos);
          BigDecimal subTotal = BigDecimal.ZERO;
          for (Taco taco : tacos) {
            BigDecimal unitPrice = taco.getPrice() != null ? taco.getPrice() : BigDecimal.ZERO;
            int qty = taco.getQuantity();
            subTotal = subTotal.add(unitPrice.multiply(BigDecimal.valueOf(qty)));
          }
          order.setSubTotal(subTotal);
          order.setTotal(subTotal);
          return order;
        })
        .flatMap(computedOrder -> {
          // Ejercicio 15: Motor de cupones con reglas y fecha de expiración
          if (couponEngine != null) {
            return couponEngine.applyCoupon(computedOrder, new java.util.Date());
          }
          return Mono.just(computedOrder);
        });
  }

  private Mono<Taco> calculateCustomTacoPrice(Taco taco) {
    if (taco.getIngredients() == null || taco.getIngredients().isEmpty()) {
      taco.setPrice(BigDecimal.ZERO);
      return Mono.just(taco);
    }

    if (ingredientRepo != null) {
      return Flux.fromIterable(taco.getIngredients())
          .flatMap(ing -> {
            if (ing.getId() != null) {
              return ingredientRepo.findById(ing.getId())
                  .defaultIfEmpty(ing);
            }
            return Mono.just(ing);
          })
          .collectList()
          .map(authoritativeIngredients -> {
            taco.setIngredients(authoritativeIngredients);
            taco.calculatePriceFromIngredients();
            return taco;
          });
    }

    taco.calculatePriceFromIngredients();
    return Mono.just(taco);
  }

  @PostMapping(path="fromEmail", consumes="application/json")
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<TacoOrder> postOrderFromEmail(@RequestBody Mono<EmailOrder> emailOrder) {
    return emailOrderService.convertEmailOrderToDomainOrder(emailOrder)
        .flatMap(this::validateTacoPhysics)
        .flatMap(this::calculateOrderPrices)
        .flatMap(ord -> {
          if (inventoryService != null) {
            return inventoryService.reserveInventory(ord);
          }
          return Mono.just(ord);
        })
        .flatMap(repo::save)
        .doOnNext(savedOrder -> {
          if (orderMessages != null) {
            orderMessages.sendOrder(savedOrder);
            orderMessages.sendOrderEvent(OrderEvent.fromOrder(savedOrder, OrderEventType.ORDER_CREATED));
          }
        });
  }

  //Ejercicio 5: PUT y DELETE de órdenes con identidad consistente
  @PutMapping(path="/{orderId}", consumes="application/json")
  //Agregar @PathVariable con id "orderId".
  public Mono<TacoOrder> putOrder(@PathVariable("orderId") String orderId,
                                  @RequestBody TacoOrder order) {
    order.setId(orderId); 
    return calculateOrderPrices(order)
        .flatMap(repo::save);
  }

  @PatchMapping(path="/{orderId}", consumes="application/json")
  public Mono<TacoOrder> patchOrder(@PathVariable("orderId") String orderId,
                          @RequestBody TacoOrder patch) {

    return repo.findById(orderId)
        .map(order -> {
          if (patch.getDeliveryName() != null) {
            order.setDeliveryName(patch.getDeliveryName());
          }
          if (patch.getDeliveryStreet() != null) {
            order.setDeliveryStreet(patch.getDeliveryStreet());
          }
          if (patch.getDeliveryCity() != null) {
            order.setDeliveryCity(patch.getDeliveryCity());
          }
          if (patch.getDeliveryState() != null) {
            order.setDeliveryState(patch.getDeliveryState());
          }
          if (patch.getDeliveryZip() != null) {
            order.setDeliveryZip(patch.getDeliveryZip()); //Ejercicio 4: Arreglar error de copy-paste en el patch de la dirección de entrega. Se estaba seteando el estado en lugar del zip.
                                                          //También asegurarnos de que solo se puedan actualizar los campos de dirección de entrega y no el usuario ni la lista de tacos.
          }
          if (patch.getPaymentToken() != null) {
            order.setPaymentToken(patch.getPaymentToken());
          }
          if (patch.getCcExpiration() != null) {
            order.setCcExpiration(patch.getCcExpiration());
          }
          if (patch.getLast4() != null) {
            order.setLast4(patch.getLast4());
          }
          return order;
        })
        .flatMap(repo::save).switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
  }

  // Ejercicio 16: Reservar y liberar inventario sin vender aire
  // Ejercicio 23: Historial paginado y privado de órdenes
  @DeleteMapping("/{orderId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public Mono<Void> deleteOrder(@PathVariable("orderId") String orderId,
                                @Autowired(required = false) Principal principal) {
    if (principal == null) {
      if (inventoryService != null) {
        return repo.findById(orderId)
            .flatMap(inventoryService::releaseInventory)
            .then(Mono.defer(() -> repo.deleteById(orderId)));
      }
      return repo.deleteById(orderId);
    }

    return resolveAuthenticatedUser(principal)
        .flatMap(user -> repo.findById(orderId)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada: " + orderId)))
            .flatMap(order -> {
              if (!isOrderOwnedByUser(order, user)) {
                return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Acceso denegado: No está autorizado para cancelar la orden de otro usuario."));
              }
              if (inventoryService != null) {
                return inventoryService.releaseInventory(order)
                    .then(Mono.defer(() -> repo.deleteById(orderId)));
              }
              return repo.deleteById(orderId);
            }));
  }

  private Mono<User> resolveAuthenticatedUser(Principal principal) {
    if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
      return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
    }

    if (principal instanceof Authentication) {
      Object p = ((Authentication) principal).getPrincipal();
      if (p instanceof User) {
        return Mono.just((User) p);
      }
    }

    if (userRepo != null) {
      return userRepo.findByUsername(principal.getName().trim())
          .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
              "Usuario autenticado no encontrado: " + principal.getName())));
    }

    User fallbackUser = new User(principal.getName(), "PROTECTED", principal.getName(), null, null, null, null, null, null);
    fallbackUser.setId(principal.getName());
    return Mono.just(fallbackUser);
  }

  private boolean isOrderOwnedByUser(TacoOrder order, User user) {
    if (order == null || user == null) {
      return false;
    }
    if (order.getUser() == null) {
      return false;
    }
    if (order.getUser().getId() != null && user.getId() != null
        && order.getUser().getId().equals(user.getId())) {
      return true;
    }
    if (order.getUser().getUsername() != null && user.getUsername() != null
        && order.getUser().getUsername().equalsIgnoreCase(user.getUsername())) {
      return true;
    }
    return false;
  }

  private boolean isUserAdmin(Principal principal) {
    if (principal == null) {
      return false;
    }
    if (principal instanceof Authentication) {
      Authentication auth = (Authentication) principal;
      if (auth.getAuthorities() != null) {
        for (org.springframework.security.core.GrantedAuthority ga : auth.getAuthorities()) {
          if ("ROLE_ADMIN".equalsIgnoreCase(ga.getAuthority()) || "ADMIN".equalsIgnoreCase(ga.getAuthority())) {
            return true;
          }
        }
      }
    }
    return "admin".equalsIgnoreCase(principal.getName().trim());
  }

}
