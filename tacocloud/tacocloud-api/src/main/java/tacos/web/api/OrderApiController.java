package tacos.web.api;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.data.IngredientRepository;
import tacos.data.OrderRepository;
import tacos.data.TacoRepository;
import tacos.messaging.OrderMessagingService;

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

  public OrderApiController(OrderRepository repo,
                            OrderMessagingService orderMessages,
                            EmailOrderService emailOrderService) {
    this(repo, orderMessages, emailOrderService, null, null, null);
  }

  public OrderApiController(OrderRepository repo,
                            OrderMessagingService orderMessages,
                            EmailOrderService emailOrderService,
                            IngredientRepository ingredientRepo,
                            TacoRepository tacoRepo) {
    this(repo, orderMessages, emailOrderService, ingredientRepo, tacoRepo, null);
  }

  @Autowired
  public OrderApiController(OrderRepository repo,
                            OrderMessagingService orderMessages,
                            EmailOrderService emailOrderService,
                            IngredientRepository ingredientRepo,
                            TacoRepository tacoRepo,
                            CouponEngine couponEngine) {
    this.repo = repo;
    this.orderMessages = orderMessages;
    this.emailOrderService = emailOrderService;
    this.ingredientRepo = ingredientRepo;
    this.tacoRepo = tacoRepo;
    this.couponEngine = couponEngine;
  }

  @GetMapping(produces="application/json")
  public Flux<TacoOrder> allOrders() {
    return repo.findAll();
  }

//  @PostMapping(consumes="application/json")
//  @ResponseStatus(HttpStatus.CREATED)
//  public Mono<Order> postOrder(@RequestBody Mono<Order> order) {
//    order.subscribe(orderMessages::sendOrder); // TODO: not ideal...work into reactive flow below
//    return order
//        .flatMap(repo::save);
//  }

  // Ejercicio 14: Calcular precios y cantidades del lado servidor
  @PostMapping(consumes="application/json")
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<TacoOrder> postOrder(@RequestBody TacoOrder order) {
    return calculateOrderPrices(order)
        .flatMap(repo::save)
        .doOnNext(orderMessages::sendOrder);
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
        .flatMap(this::calculateOrderPrices)
        .flatMap(repo::save)
        .doOnNext(orderMessages::sendOrder);
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

  @DeleteMapping("/{orderId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public Mono<Void> deleteOrder(@PathVariable("orderId") String orderId) {
    try {
      repo.deleteById(orderId);
    } catch (EmptyResultDataAccessException e) {}
    return Mono.empty();
  }

}
