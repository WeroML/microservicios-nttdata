package tacos;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 15: Motor de cupones con reglas y fecha de expiración
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document
public class Coupon implements Serializable {
  private static final long serialVersionUID = 1L;

  public enum DiscountType {
    PERCENTAGE,
    FIXED_AMOUNT
  }

  @Id
  private String id;

  @Indexed(unique = true)
  private String code;

  private String description;

  private DiscountType discountType = DiscountType.PERCENTAGE;

  private BigDecimal discountValue = BigDecimal.ZERO;

  // Reglas de negocio
  private BigDecimal minimumOrderAmount; // Regla de monto mínimo (opcional)

  private Date validFrom; // Fecha de inicio de validez (opcional)

  private Date expiresAt; // Regla de expiración

  private Integer maxUsages; // Límite de usos totales (opcional)

  private Integer usageCount = 0; // Usos consumidos

  private Boolean active = true;

  public Coupon(String code, String description, DiscountType discountType, BigDecimal discountValue,
      BigDecimal minimumOrderAmount, Date validFrom, Date expiresAt, Integer maxUsages, Boolean active) {
    this.code = code;
    this.description = description;
    this.discountType = discountType;
    this.discountValue = discountValue;
    this.minimumOrderAmount = minimumOrderAmount;
    this.validFrom = validFrom;
    this.expiresAt = expiresAt;
    this.maxUsages = maxUsages;
    this.usageCount = 0;
    this.active = active;
  }

  /**
   * Valida si el cupón ha expirado respecto a la fecha de referencia.
   */
  public boolean isExpired(Date now) {
    if (expiresAt == null) {
      return false;
    }
    Date reference = (now != null) ? now : new Date();
    return reference.after(expiresAt);
  }

  /**
   * Valida si el cupón aún no entra en periodo de vigencia.
   */
  public boolean isNotYetValid(Date now) {
    if (validFrom == null) {
      return false;
    }
    Date reference = (now != null) ? now : new Date();
    return reference.before(validFrom);
  }

  /**
   * Valida si el monto de la orden cumple con el monto mínimo requerido.
   */
  public boolean meetsMinimumAmount(BigDecimal orderAmount) {
    if (minimumOrderAmount == null || minimumOrderAmount.compareTo(BigDecimal.ZERO) <= 0) {
      return true;
    }
    if (orderAmount == null) {
      return false;
    }
    return orderAmount.compareTo(minimumOrderAmount) >= 0;
  }

  /**
   * Valida si el cupón ha superado el límite de usos permitidos.
   */
  public boolean isUsageLimitReached() {
    if (maxUsages == null || maxUsages <= 0) {
      return false;
    }
    return usageCount != null && usageCount >= maxUsages;
  }

  /**
   * Valida exhaustivamente todas las reglas del cupón contra la fecha y monto de la orden.
   */
  public void validateRules(Date now, BigDecimal orderAmount) {
    if (!Boolean.TRUE.equals(this.active)) {
      throw new IllegalStateException("El cupón '" + code + "' no está activo.");
    }
    if (isNotYetValid(now)) {
      throw new IllegalStateException("El cupón '" + code + "' aún no es válido.");
    }
    if (isExpired(now)) {
      throw new IllegalStateException("El cupón '" + code + "' ha expirado el " + expiresAt + ".");
    }
    if (!meetsMinimumAmount(orderAmount)) {
      throw new IllegalStateException("El pedido no alcanza el monto mínimo de $" + minimumOrderAmount + " requerido por el cupón '" + code + "'.");
    }
    if (isUsageLimitReached()) {
      throw new IllegalStateException("El cupón '" + code + "' ha alcanzado su límite máximo de usos (" + maxUsages + ").");
    }
  }

  /**
   * Calcula el descuento aplicable garantizando que nunca supere el monto de la orden.
   */
  public BigDecimal calculateDiscount(BigDecimal orderAmount) {
    if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    if (discountValue == null || discountValue.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    BigDecimal discount;
    if (discountType == DiscountType.PERCENTAGE) {
      discount = orderAmount.multiply(discountValue)
          .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    } else {
      discount = discountValue.setScale(2, RoundingMode.HALF_UP);
    }

    if (discount.compareTo(orderAmount) > 0) {
      return orderAmount.setScale(2, RoundingMode.HALF_UP);
    }
    return discount;
  }
}
