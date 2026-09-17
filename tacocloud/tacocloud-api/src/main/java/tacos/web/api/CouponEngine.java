package tacos.web.api;

import java.math.BigDecimal;
import java.util.Date;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;
import tacos.Coupon;
import tacos.TacoOrder;
import tacos.data.CouponRepository;

// Ejercicio 15: Motor de cupones con reglas y fecha de expiración
@Service
public class CouponEngine {

  private final CouponRepository couponRepo;

  public CouponEngine(CouponRepository couponRepo) {
    this.couponRepo = couponRepo;
  }

  /**
   * Valida y aplica las reglas de un cupón a una orden existente.
   * Si la orden no tiene couponCode, calcula el total sin descuento.
   * Si tiene couponCode, busca el cupón, valida reglas (activo, vigencia, monto mínimo, usos),
   * calcula el descuento oficial en el servidor y actualiza subTotal, discount y total.
   */
  public Mono<TacoOrder> applyCoupon(TacoOrder order, Date evaluationDate) {
    if (order == null) {
      return Mono.empty();
    }
    String code = order.getCouponCode();
    if (code == null || code.trim().isEmpty()) {
      order.setDiscount(BigDecimal.ZERO);
      order.calculateTotal();
      return Mono.just(order);
    }

    Date date = (evaluationDate != null) ? evaluationDate : new Date();

    return couponRepo.findByCodeIgnoreCase(code.trim())
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cupón no válido o inexistente: " + code)))
        .flatMap(coupon -> {
          try {
            coupon.validateRules(date, order.getSubTotal());
          } catch (IllegalStateException e) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()));
          }

          BigDecimal discount = coupon.calculateDiscount(order.getSubTotal());
          order.setCouponCode(coupon.getCode());
          order.setDiscount(discount);
          order.calculateTotal();

          coupon.setUsageCount(coupon.getUsageCount() == null ? 1 : coupon.getUsageCount() + 1);
          return couponRepo.save(coupon).thenReturn(order);
        });
  }

  /**
   * Valida un cupón y retorna el resultado y descuento estimado sin aplicarlo a una orden.
   */
  public Mono<CouponValidationResult> validateCoupon(String code, BigDecimal orderAmount, Date evaluationDate) {
    if (code == null || code.trim().isEmpty()) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código de cupón no puede estar vacío"));
    }
    Date date = (evaluationDate != null) ? evaluationDate : new Date();
    BigDecimal amount = (orderAmount != null) ? orderAmount : BigDecimal.ZERO;

    return couponRepo.findByCodeIgnoreCase(code.trim())
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Cupón no encontrado: " + code)))
        .flatMap(coupon -> {
          try {
            coupon.validateRules(date, amount);
          } catch (IllegalStateException e) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()));
          }
          BigDecimal discount = coupon.calculateDiscount(amount);
          return Mono.just(new CouponValidationResult(coupon.getCode(), coupon.getDescription(),
              coupon.getDiscountType().name(), coupon.getDiscountValue(), discount, true, "Cupón válido y aplicable"));
        });
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CouponValidationResult {
    private String code;
    private String description;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal calculatedDiscount;
    private boolean valid;
    private String message;
  }
}
