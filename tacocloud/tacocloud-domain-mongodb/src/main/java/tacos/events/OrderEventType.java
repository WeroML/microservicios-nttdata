package tacos.events;

import tacos.TacoOrder.OrderStatus;

// Ejercicio 27: Contrato único de eventos de orden
public enum OrderEventType {
  ORDER_CREATED,
  ORDER_CONFIRMED,
  ORDER_PREPARING,
  ORDER_READY,
  ORDER_DELIVERING,
  ORDER_DELIVERED,
  ORDER_CANCELLED;

  public static OrderEventType fromOrderStatus(OrderStatus status) {
    if (status == null) {
      return ORDER_CREATED;
    }
    switch (status) {
      case PENDING:
        return ORDER_CREATED;
      case CONFIRMED:
        return ORDER_CONFIRMED;
      case PREPARING:
        return ORDER_PREPARING;
      case READY:
        return ORDER_READY;
      case DELIVERING:
        return ORDER_DELIVERING;
      case DELIVERED:
        return ORDER_DELIVERED;
      case CANCELLED:
        return ORDER_CANCELLED;
      default:
        return ORDER_CREATED;
    }
  }
}
