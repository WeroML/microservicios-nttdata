package tacos.web.api.v2;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.data.OrderRepository;
import tacos.web.api.OrderApiController;
import tacos.web.api.dto.PagedResponse;
import tacos.web.api.dto.v2.OrderV2Dto.AddressDto;
import tacos.web.api.dto.v2.OrderV2Dto.OrderItemV2Request;
import tacos.web.api.dto.v2.OrderV2Dto.OrderItemV2Response;
import tacos.web.api.dto.v2.OrderV2Dto.OrderTrackingDto;
import tacos.web.api.dto.v2.OrderV2Dto.OrderV2Request;
import tacos.web.api.dto.v2.OrderV2Dto.OrderV2Response;
import tacos.web.api.dto.v2.OrderV2Dto.PricingBreakdownDto;

// Ejercicio 35: Versionar la API y publicar contrato OpenAPI
@RestController
@RequestMapping(path = "/api/v2/orders", produces = "application/json")
@CrossOrigin(origins = "*")
public class OrderApiV2Controller {

  private final OrderApiController v1Controller;
  private final OrderRepository orderRepo;

  @Autowired
  public OrderApiV2Controller(OrderApiController v1Controller, OrderRepository orderRepo) {
    this.v1Controller = v1Controller;
    this.orderRepo = orderRepo;
  }

  @PostMapping(consumes = "application/json")
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<OrderV2Response> postOrderV2(
      @RequestBody OrderV2Request request,
      @Autowired(required = false) Principal principal,
      @Autowired(required = false) ServerWebExchange exchange) {

    if (request == null) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cuerpo de la orden V2 no puede estar vacío"));
    }

    TacoOrder order = convertToTacoOrder(request);

    return v1Controller.postOrder(order, principal, exchange)
        .map(this::mapToV2Response);
  }

  @GetMapping("/{id}")
  public Mono<OrderV2Response> getOrderV2ById(
      @PathVariable("id") String id,
      @Autowired(required = false) Principal principal) {

    return v1Controller.getOrderById(id, principal)
        .map(this::mapToV2Response);
  }

  @GetMapping
  public Mono<PagedResponse<OrderV2Response>> allOrdersV2(
      @Autowired(required = false) Principal principal) {

    return v1Controller.allOrders(0, 50, principal)
        .map(resp -> {
          PagedResponse<TacoOrder> paged = resp.getBody();
          if (paged == null || paged.getContent() == null) {
            return PagedResponse.of(java.util.Collections.emptyList(), 0, 50);
          }
          List<OrderV2Response> v2List = new ArrayList<>();
          for (TacoOrder ord : paged.getContent()) {
            v2List.add(mapToV2Response(ord));
          }
          return new PagedResponse<>(
              v2List,
              paged.getPage(),
              paged.getSize(),
              paged.getTotalElements(),
              paged.getTotalPages(),
              paged.isFirst(),
              paged.isLast(),
              paged.isHasNext(),
              paged.isHasPrevious()
          );
        });
  }

  public TacoOrder convertToTacoOrder(OrderV2Request request) {
    TacoOrder order = new TacoOrder();
    order.setDeliveryName(request.getDeliveryName());

    if (request.getDeliveryAddress() != null) {
      order.setDeliveryStreet(request.getDeliveryAddress().getStreet());
      order.setDeliveryCity(request.getDeliveryAddress().getCity());
      order.setDeliveryState(request.getDeliveryAddress().getState());
      order.setDeliveryZip(request.getDeliveryAddress().getZip());
    }

    order.setCouponCode(request.getCouponCode());
    order.setToken(request.getPaymentToken());
    order.setIdempotencyKey(request.getIdempotencyKey());

    if (request.getItems() != null) {
      for (OrderItemV2Request item : request.getItems()) {
        if (item == null) continue;
        Taco taco = new Taco();
        taco.setName(item.getName() != null ? item.getName() : "Custom Taco");
        taco.setQuantity(item.getQuantity() != null && item.getQuantity() > 0 ? item.getQuantity() : 1);

        if (item.getIngredientIds() != null) {
          List<Ingredient> ingredients = new ArrayList<>();
          for (String ingId : item.getIngredientIds()) {
            if (ingId != null && !ingId.trim().isEmpty()) {
              ingredients.add(new Ingredient(ingId.trim(), ingId.trim(), Ingredient.Type.WRAP));
            }
          }
          taco.setIngredients(ingredients);
        }
        order.addTaco(taco);
      }
    }

    return order;
  }

  public OrderV2Response mapToV2Response(TacoOrder order) {
    if (order == null) return null;

    AddressDto address = AddressDto.builder()
        .street(order.getDeliveryStreet())
        .city(order.getDeliveryCity())
        .state(order.getDeliveryState())
        .zip(order.getDeliveryZip())
        .build();

    BigDecimal subTotal = order.getSubTotal() != null ? order.getSubTotal() : BigDecimal.ZERO;
    BigDecimal discount = order.getDiscount() != null ? order.getDiscount() : BigDecimal.ZERO;
    BigDecimal total = order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO;

    PricingBreakdownDto pricing = PricingBreakdownDto.builder()
        .subTotal(subTotal)
        .discount(discount)
        .tax(BigDecimal.ZERO)
        .total(total)
        .build();

    List<OrderItemV2Response> items = new ArrayList<>();
    if (order.getTacos() != null) {
      for (Taco taco : order.getTacos()) {
        if (taco == null) continue;
        List<String> ingIds = new ArrayList<>();
        if (taco.getIngredients() != null) {
          for (Ingredient ing : taco.getIngredients()) {
            if (ing != null && ing.getId() != null) {
              ingIds.add(ing.getId());
            }
          }
        }
        BigDecimal unitPrice = taco.getPrice() != null ? taco.getPrice() : BigDecimal.ZERO;
        int qty = (taco.getQuantity() != null && taco.getQuantity() > 0) ? taco.getQuantity() : 1;
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));

        items.add(OrderItemV2Response.builder()
            .name(taco.getName())
            .quantity(qty)
            .ingredientIds(ingIds)
            .unitPrice(unitPrice)
            .lineTotal(lineTotal)
            .build());
      }
    }

    OrderTrackingDto tracking = OrderTrackingDto.builder()
        .estimatedMinutes(order.getEstimatedPrepMinutes() != null ? order.getEstimatedPrepMinutes() : 15)
        .queuePosition(1)
        .statusUrl("/api/v2/orders/" + order.getId())
        .build();

    Map<String, String> links = new LinkedHashMap<>();
    links.put("self", "/api/v2/orders/" + order.getId());
    links.put("v1_equivalent", "/api/v1/orders/" + order.getId());
    links.put("status", "/api/v2/orders/" + order.getId() + "/status");
    links.put("history", "/api/v2/orders");

    return OrderV2Response.builder()
        .id(order.getId())
        .version("2.0")
        .status(order.getStatus() != null ? order.getStatus().name() : "CONFIRMED")
        .placedAt(order.getPlacedAt())
        .correlationId(order.getCorrelationId())
        .idempotencyKey(order.getIdempotencyKey())
        .customerName(order.getDeliveryName())
        .deliveryAddress(address)
        .pricingBreakdown(pricing)
        .items(items)
        .tracking(tracking)
        .links(links)
        .build();
  }
}
