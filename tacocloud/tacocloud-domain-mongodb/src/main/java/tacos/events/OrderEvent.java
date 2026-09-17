package tacos.events;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.TacoOrder;

// Ejercicio 27: Contrato único de eventos de orden
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent implements Serializable {

  private static final long serialVersionUID = 1L;
  public static final String CONTRACT_VERSION = "1.0";
  public static final String DEFAULT_SOURCE = "tacocloud-api";

  @Builder.Default
  private String eventId = UUID.randomUUID().toString();

  private OrderEventType eventType;

  @Builder.Default
  private Date timestamp = new Date();

  @Builder.Default
  private String version = CONTRACT_VERSION;

  @Builder.Default
  private String source = DEFAULT_SOURCE;

  private String correlationId;
  private String orderId;
  private OrderEventPayload payload;

  public static OrderEvent fromOrder(TacoOrder order, OrderEventType eventType) {
    return fromOrder(order, eventType, DEFAULT_SOURCE, null);
  }

  public static OrderEvent fromOrder(TacoOrder order, OrderEventType eventType, String source) {
    return fromOrder(order, eventType, source, null);
  }

  public static OrderEvent fromOrder(TacoOrder order, OrderEventType eventType, String source, String correlationId) {
    String ordId = order != null ? order.getId() : null;
    return OrderEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .eventType(eventType != null ? eventType : OrderEventType.ORDER_CREATED)
        .timestamp(new Date())
        .version(CONTRACT_VERSION)
        .source(source != null ? source : DEFAULT_SOURCE)
        .correlationId(correlationId != null ? correlationId : UUID.randomUUID().toString())
        .orderId(ordId)
        .payload(OrderEventPayload.fromOrder(order))
        .build();
  }
}
