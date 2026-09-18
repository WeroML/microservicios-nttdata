package tacos.web.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.TacoOrder.OrderStatus;
import tacos.User;
import tacos.data.OrderRepository;
import tacos.events.OrderEvent;
import tacos.events.OrderEventType;
import tacos.messaging.OrderMessagingService;
import tacos.web.api.dto.ClaimOrderRequest;
import tacos.web.api.dto.KitchenQueueItem;
import tacos.web.api.dto.KitchenQueueResponse;
import tacos.web.api.dto.OrderEtaResponse;

// Ejercicio 26: Cola de cocina, claim atómico y tiempo estimado
// Ejercicio 27: Contrato único de eventos de orden
// Ejercicio 29: Outbox transaccional para no perder órdenes
@Service
public class KitchenService {

  private final OrderRepository repo;
  private final OrderMessagingService orderMessages;
  private final tacos.outbox.TransactionalOutboxService outboxService;

  public KitchenService(OrderRepository repo) {
    this(repo, null, null);
  }

  public KitchenService(OrderRepository repo, OrderMessagingService orderMessages) {
    this(repo, orderMessages, null);
  }

  @Autowired
  public KitchenService(
      OrderRepository repo,
      @Autowired(required = false) OrderMessagingService orderMessages,
      @Autowired(required = false) tacos.outbox.TransactionalOutboxService outboxService) {
    this.repo = repo;
    this.orderMessages = orderMessages;
    this.outboxService = outboxService;
  }

  private Mono<?> dispatchEvent(OrderEvent event) {
    if (outboxService != null) {
      return outboxService.enqueueEvent(event);
    } else if (orderMessages != null) {
      orderMessages.sendOrderEvent(event);
      return Mono.empty();
    }
    return Mono.empty();
  }

  /**
   * Obtiene la cola activa de cocina ordenada cronológicamente (FIFO).
   * Contiene órdenes en estado PREPARING y CONFIRMED.
   */
  public Mono<KitchenQueueResponse> getKitchenQueue() {
    return repo.findAll()
        .filter(order -> order.getStatus() == OrderStatus.PREPARING || order.getStatus() == OrderStatus.CONFIRMED)
        .collectList()
        .map(orders -> {
          // Ordenar FIFO por fecha de colocación (las más antiguas primero)
          orders.sort((o1, o2) -> {
            Date d1 = o1.getPlacedAt() != null ? o1.getPlacedAt() : new Date(0);
            Date d2 = o2.getPlacedAt() != null ? o2.getPlacedAt() : new Date(0);
            return d1.compareTo(d2);
          });

          List<KitchenQueueItem> items = new ArrayList<>();
          int waitingCount = 0;
          int preparingCount = 0;
          int totalWaitMinutes = 0;
          long now = System.currentTimeMillis();

          for (int i = 0; i < orders.size(); i++) {
            TacoOrder ord = orders.get(i);
            int ordersAhead = i;
            int queuePosition = i + 1;

            if (ord.getStatus() == OrderStatus.PREPARING) {
              preparingCount++;
            } else if (ord.getStatus() == OrderStatus.CONFIRMED) {
              waitingCount++;
            }

            int prepMinutes = calculateEstimatedMinutes(ord, ordersAhead);
            totalWaitMinutes += prepMinutes;

            long elapsedMinutes = 0;
            if (ord.getPlacedAt() != null) {
              elapsedMinutes = Math.max(0, (now - ord.getPlacedAt().getTime()) / 60000L);
            }

            Date readyAt = ord.getEstimatedReadyAt();
            if (readyAt == null) {
              readyAt = new Date(now + (prepMinutes * 60000L));
            }

            items.add(toQueueItem(ord, queuePosition, ordersAhead, prepMinutes, readyAt, elapsedMinutes));
          }

          return KitchenQueueResponse.builder()
              .totalInQueue(orders.size())
              .totalWaiting(waitingCount)
              .totalPreparing(preparingCount)
              .estimatedQueueWaitMinutes(totalWaitMinutes)
              .orders(items)
              .build();
        });
  }

  /**
   * Operación de Claim Atómico:
   * Solo permite tomar la orden si está en estado CONFIRMED y claimedBy es nulo.
   * Si ya fue tomada por otro cocinero o está en otro estado, lanza 409 Conflict.
   */
  public Mono<KitchenQueueItem> claimOrder(String orderId, String chefId, ClaimOrderRequest request) {
    return repo.findById(orderId)
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada: " + orderId)))
        .flatMap(order -> {
          // Validación atómica de concurrencia
          if (order.getClaimedBy() != null) {
            return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                "Conflicto de concurrencia: La orden '" + orderId + "' ya ha sido reclamada por el cocinero '" +
                order.getClaimedBy() + "' en fecha " + order.getClaimedAt() + "."));
          }

          if (order.getStatus() != OrderStatus.CONFIRMED) {
            return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                "Conflicto de estado: No es posible tomar la orden '" + orderId +
                "'. Su estado actual es '" + order.getStatus() + "' y debe estar en 'CONFIRMED'."));
          }

          // Asignación atómica a estación y cocinero
          String effectiveChef = (chefId != null && !chefId.trim().isEmpty()) ? chefId.trim() : "chef_cocina";
          order.setStatus(OrderStatus.PREPARING);
          order.setClaimedBy(effectiveChef);
          Date claimDate = new Date();
          order.setClaimedAt(claimDate);

          int prepMinutes = calculateEstimatedMinutes(order, 0);
          order.setEstimatedPrepMinutes(prepMinutes);
          Date readyAt = new Date(claimDate.getTime() + (prepMinutes * 60000L));
          order.setEstimatedReadyAt(readyAt);

          return repo.save(order)
              .flatMap(saved -> {
                OrderEvent evt = OrderEvent.fromOrder(saved, OrderEventType.ORDER_PREPARING);
                return dispatchEvent(evt)
                    .thenReturn(toQueueItem(saved, 1, 0, prepMinutes, readyAt, 0L));
              });
        });
  }

  /**
   * Libera una orden tomada en preparación (Unclaim).
   * Vuelve a colocar la orden en estado CONFIRMED y vacía claimedBy y claimedAt.
   */
  public Mono<KitchenQueueItem> unclaimOrder(String orderId, String chefId, boolean isAdmin) {
    return repo.findById(orderId)
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada: " + orderId)))
        .flatMap(order -> {
          if (order.getStatus() != OrderStatus.PREPARING || order.getClaimedBy() == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La orden '" + orderId + "' no está tomada en preparación actualmente."));
          }

          // Validación de autorización: solo el cocinero que la tomó o un admin puede liberarla
          if (!isAdmin && chefId != null && !chefId.equalsIgnoreCase(order.getClaimedBy())) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Acceso denegado: No puede liberar una orden tomada por otro cocinero ('" + order.getClaimedBy() + "')."));
          }

          order.setStatus(OrderStatus.CONFIRMED);
          order.setClaimedBy(null);
          order.setClaimedAt(null);

          return repo.save(order)
              .flatMap(saved -> {
                OrderEvent evt = OrderEvent.fromOrder(saved, OrderEventType.ORDER_CONFIRMED);
                return dispatchEvent(evt)
                    .thenReturn(toQueueItem(saved, 1, 0, saved.getEstimatedPrepMinutes() != null ? saved.getEstimatedPrepMinutes() : 5, null, 0L));
              });
        });
  }

  /**
   * Consulta de tiempo estimado (ETA) y avance de la orden para el cliente y cocina.
   * Con protección IDOR y cálculo dinámico de posición en cola.
   */
  public Mono<OrderEtaResponse> getOrderEta(String orderId, User user, boolean isAdmin) {
    return repo.findById(orderId)
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada: " + orderId)))
        .flatMap(order -> {
          // Protección IDOR (Zero-Trust)
          if (!isAdmin && !isOrderOwnedByUser(order, user)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Acceso denegado: No está autorizado para consultar el tiempo estimado de la orden de otro usuario."));
          }

          // Si ya fue entregada o cancelada
          if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            return Mono.just(OrderEtaResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .queuePosition(0)
                .ordersAhead(0)
                .claimedBy(order.getClaimedBy())
                .estimatedPrepMinutes(order.getEstimatedPrepMinutes() != null ? order.getEstimatedPrepMinutes() : 0)
                .remainingMinutes(0)
                .estimatedReadyAt(order.getEstimatedReadyAt())
                .placedAt(order.getPlacedAt())
                .build());
          }

          // Calcular posición en cola relativa a órdenes CONFIRMED o PREPARING
          return repo.findAll()
              .filter(o -> o.getStatus() == OrderStatus.PREPARING || o.getStatus() == OrderStatus.CONFIRMED)
              .collectList()
              .map(queueOrders -> {
                queueOrders.sort((o1, o2) -> {
                  Date d1 = o1.getPlacedAt() != null ? o1.getPlacedAt() : new Date(0);
                  Date d2 = o2.getPlacedAt() != null ? o2.getPlacedAt() : new Date(0);
                  return d1.compareTo(d2);
                });

                int ordersAhead = 0;
                int queuePosition = 1;
                for (int i = 0; i < queueOrders.size(); i++) {
                  if (queueOrders.get(i).getId() != null && queueOrders.get(i).getId().equals(order.getId())) {
                    ordersAhead = i;
                    queuePosition = i + 1;
                    break;
                  }
                }

                int totalPrepMinutes = order.getEstimatedPrepMinutes() != null
                    ? order.getEstimatedPrepMinutes()
                    : calculateEstimatedMinutes(order, ordersAhead);

                int remainingMinutes = calculateRemainingMinutes(order, totalPrepMinutes);

                Date readyAt = order.getEstimatedReadyAt();
                if (readyAt == null) {
                  readyAt = new Date(System.currentTimeMillis() + (remainingMinutes * 60000L));
                }

                return OrderEtaResponse.builder()
                    .orderId(order.getId())
                    .status(order.getStatus())
                    .queuePosition(queuePosition)
                    .ordersAhead(ordersAhead)
                    .claimedBy(order.getClaimedBy())
                    .estimatedPrepMinutes(totalPrepMinutes)
                    .remainingMinutes(remainingMinutes)
                    .estimatedReadyAt(readyAt)
                    .placedAt(order.getPlacedAt())
                    .build();
              });
        });
  }

  /**
   * Cálculo determinista del tiempo estimado de preparación en minutos:
   * - Base por orden: 3 minutos.
   * - Por cada taco: 1.5 minutos base + 0.25 minutos (15 s) por ingrediente multiplicado por la cantidad.
   * - Demora por backlog: 2 minutos por cada orden previa en cola.
   */
  public int calculateEstimatedMinutes(TacoOrder order, int ordersAhead) {
    double baseMinutes = 3.0;
    double tacoPrepMinutes = 0.0;

    if (order.getTacos() != null) {
      for (Taco taco : order.getTacos()) {
        int qty = (taco.getQuantity() != null && taco.getQuantity() > 0) ? taco.getQuantity() : 1;
        int ingredientsCount = (taco.getIngredients() != null) ? taco.getIngredients().size() : 0;
        double singleTacoTime = 1.5 + (ingredientsCount * 0.25);
        tacoPrepMinutes += (singleTacoTime * qty);
      }
    }

    double backlogMinutes = ordersAhead * 2.0;
    return (int) Math.ceil(baseMinutes + tacoPrepMinutes + backlogMinutes);
  }

  private int calculateRemainingMinutes(TacoOrder order, int totalPrepMinutes) {
    if (order.getStatus() == OrderStatus.PREPARING && order.getClaimedAt() != null) {
      long elapsed = (System.currentTimeMillis() - order.getClaimedAt().getTime()) / 60000L;
      return (int) Math.max(1, totalPrepMinutes - elapsed);
    }
    return Math.max(1, totalPrepMinutes);
  }

  private KitchenQueueItem toQueueItem(TacoOrder ord, int queuePosition, int ordersAhead,
                                      int prepMinutes, Date readyAt, Long elapsedMinutes) {
    int tacoCount = 0;
    List<String> tacoSummary = new ArrayList<>();
    if (ord.getTacos() != null) {
      for (Taco taco : ord.getTacos()) {
        int qty = (taco.getQuantity() != null && taco.getQuantity() > 0) ? taco.getQuantity() : 1;
        tacoCount += qty;
        int ingCount = taco.getIngredients() != null ? taco.getIngredients().size() : 0;
        String name = taco.getName() != null ? taco.getName() : "Taco";
        tacoSummary.add(qty + "x " + name + " (" + ingCount + " ingredientes)");
      }
    }

    return KitchenQueueItem.builder()
        .orderId(ord.getId())
        .placedAt(ord.getPlacedAt())
        .status(ord.getStatus())
        .queuePosition(queuePosition)
        .ordersAhead(ordersAhead)
        .claimedBy(ord.getClaimedBy())
        .claimedAt(ord.getClaimedAt())
        .tacoCount(tacoCount)
        .tacoSummary(tacoSummary)
        .estimatedPrepMinutes(prepMinutes)
        .estimatedReadyAt(readyAt)
        .elapsedMinutes(elapsedMinutes)
        .build();
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
}
