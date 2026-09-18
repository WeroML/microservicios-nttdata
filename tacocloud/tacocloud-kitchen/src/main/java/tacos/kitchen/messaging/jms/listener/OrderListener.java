package tacos.kitchen.messaging.jms.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import tacos.TacoOrder;
import tacos.kitchen.KitchenUI;

// Ejercicio 30: Consumidor idempotente, retry limitado y DLQ
@Profile("jms-listener")
@Component
public class OrderListener {
  
  private final KitchenUI ui;
  private final tacos.kitchen.consumer.IdempotentOrderConsumerEngine consumerEngine;

  @Autowired
  public OrderListener(
      KitchenUI ui,
      @Autowired(required = false) tacos.kitchen.consumer.IdempotentOrderConsumerEngine consumerEngine) {
    this.ui = ui;
    this.consumerEngine = consumerEngine;
  }

  public OrderListener(KitchenUI ui) {
    this(ui, null);
  }

  @JmsListener(destination = "tacocloud.order.queue")
  public void receiveOrder(TacoOrder order) {
    String key = order != null ? order.getId() : null;
    if (consumerEngine != null) {
      consumerEngine.consumeOrder(order, "JMS", key);
    } else if (ui != null) {
      ui.displayOrder(order);
    }
  }
  
}
