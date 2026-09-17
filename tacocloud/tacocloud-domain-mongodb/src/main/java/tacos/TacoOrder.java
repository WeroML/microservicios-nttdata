package tacos;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document
public class TacoOrder implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id
  private String id;
  private Date placedAt = new Date();

  private User user;

  private String deliveryName;

  private String deliveryStreet;

  private String deliveryCity;

  private String deliveryState;

  private String deliveryZip;

  // PCI-DSS: Tokenizar pago y eliminar PAN/CVV del dominio
  private String paymentToken;

  private String ccExpiration;

  private String last4;


  private List<Taco> tacos = new ArrayList<>();

  public void addTaco(Taco design) {
    this.tacos.add(design);
  }

  public String getToken() {
    return this.paymentToken;
  }

  public void setToken(String token) {
    this.paymentToken = token;
  }

  // Ejercicio 14: Calcular precios y cantidades del lado servidor
  private java.math.BigDecimal total = java.math.BigDecimal.ZERO;

  // Ejercicio 15: Motor de cupones con reglas y fecha de expiración
  private String couponCode;
  private java.math.BigDecimal subTotal = java.math.BigDecimal.ZERO;
  private java.math.BigDecimal discount = java.math.BigDecimal.ZERO;

  public java.math.BigDecimal calculateTotal() {
    java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
    if (this.tacos != null) {
      for (Taco taco : this.tacos) {
        if (taco != null && taco.getPrice() != null) {
          int qty = (taco.getQuantity() != null && taco.getQuantity() > 0) ? taco.getQuantity() : 1;
          sum = sum.add(taco.getPrice().multiply(java.math.BigDecimal.valueOf(qty)));
        }
      }
    }
    this.subTotal = sum;
    java.math.BigDecimal disc = (this.discount != null && this.discount.compareTo(java.math.BigDecimal.ZERO) > 0) ? this.discount : java.math.BigDecimal.ZERO;
    this.total = sum.subtract(disc).max(java.math.BigDecimal.ZERO);
    return this.total;
  }

  // Ejercicio 16: Reservar y liberar inventario sin vender aire
  // Ejercicio 25: Flujo de estados de una orden
  public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PREPARING,
    READY,
    DELIVERING,
    DELIVERED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus next) {
      if (next == null) {
        return false;
      }
      switch (this) {
        case PENDING:
          return next == CONFIRMED || next == CANCELLED;
        case CONFIRMED:
          return next == PREPARING || next == CANCELLED;
        case PREPARING:
          return next == READY || next == CANCELLED;
        case READY:
          return next == DELIVERING || next == CANCELLED;
        case DELIVERING:
          return next == DELIVERED || next == CANCELLED;
        case DELIVERED:
        case CANCELLED:
        default:
          return false;
      }
    }

    public java.util.Set<OrderStatus> allowedNextStates() {
      java.util.Set<OrderStatus> set = new java.util.LinkedHashSet<>();
      for (OrderStatus target : OrderStatus.values()) {
        if (canTransitionTo(target)) {
          set.add(target);
        }
      }
      return set;
    }

    public boolean isTerminal() {
      return this == DELIVERED || this == CANCELLED;
    }
  }
  private OrderStatus status = OrderStatus.CONFIRMED;

}
