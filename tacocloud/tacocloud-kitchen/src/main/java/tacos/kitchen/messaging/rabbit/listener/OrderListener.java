package tacos.kitchen.messaging.rabbit.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import tacos.TacoOrder;
import tacos.kitchen.KitchenUI;

// Ejercicio 30: Consumidor idempotente, retry limitado y DLQ
@Profile("rabbitmq-listener")
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

  @RabbitListener(queues = "tacocloud.order.queue")
  public void receiveOrder(TacoOrder order) {
    String key = order != null ? order.getId() : null;
    if (consumerEngine != null) {
      consumerEngine.consumeOrder(order, "RABBITMQ", key);
    } else if (ui != null) {
      ui.displayOrder(order);
    }
  }
  
}
