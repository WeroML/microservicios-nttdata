package tacos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Date;

import org.junit.jupiter.api.Test;
import tacos.Coupon.DiscountType;

// Ejercicio 15: Motor de cupones con reglas y fecha de expiración
public class CouponRulesDomainTest {

  @Test
  public void calculateDiscount_percentageDiscountShouldComputeCorrectly() {
    Coupon coupon = new Coupon();
    coupon.setCode("TACO10");
    coupon.setDiscountType(DiscountType.PERCENTAGE);
    coupon.setDiscountValue(new BigDecimal("10")); // 10%

    BigDecimal discount = coupon.calculateDiscount(new BigDecimal("50.00"));
    assertThat(discount).isEqualByComparingTo(new BigDecimal("5.00"));

    BigDecimal discount2 = coupon.calculateDiscount(new BigDecimal("15.50"));
    assertThat(discount2).isEqualByComparingTo(new BigDecimal("1.55"));
  }

  @Test
  public void calculateDiscount_fixedDiscountShouldNotExceedTotal() {
    Coupon coupon = new Coupon();
    coupon.setCode("FIVEOFF");
    coupon.setDiscountType(DiscountType.FIXED_AMOUNT);
    coupon.setDiscountValue(new BigDecimal("5.00"));

    BigDecimal discount = coupon.calculateDiscount(new BigDecimal("20.00"));
    assertThat(discount).isEqualByComparingTo(new BigDecimal("5.00"));

    // Descuento mayor al total: se acota al total para no dejar total negativo
    Coupon bigCoupon = new Coupon();
    bigCoupon.setCode("BIG20");
    bigCoupon.setDiscountType(DiscountType.FIXED_AMOUNT);
    bigCoupon.setDiscountValue(new BigDecimal("20.00"));

    BigDecimal cappedDiscount = bigCoupon.calculateDiscount(new BigDecimal("12.00"));
    assertThat(cappedDiscount).isEqualByComparingTo(new BigDecimal("12.00"));
  }

  @Test
  public void validateRules_whenExpired_shouldThrowException() {
    long pastTime = System.currentTimeMillis() - 86400000L; // ayer
    Coupon expiredCoupon = new Coupon();
    expiredCoupon.setCode("OLDCOUPON");
    expiredCoupon.setExpiresAt(new Date(pastTime));
    expiredCoupon.setActive(true);

    assertThat(expiredCoupon.isExpired(new Date())).isTrue();
    assertThatThrownBy(() -> expiredCoupon.validateRules(new Date(), new BigDecimal("30.00")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ha expirado");
  }

  @Test
  public void validateRules_whenNotYetValid_shouldThrowException() {
    long futureTime = System.currentTimeMillis() + 86400000L; // mañana
    Coupon futureCoupon = new Coupon();
    futureCoupon.setCode("FUTURE");
    futureCoupon.setValidFrom(new Date(futureTime));
    futureCoupon.setActive(true);

    assertThat(futureCoupon.isNotYetValid(new Date())).isTrue();
    assertThatThrownBy(() -> futureCoupon.validateRules(new Date(), new BigDecimal("30.00")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("aún no es válido");
  }

  @Test
  public void validateRules_whenOrderBelowMinimum_shouldThrowException() {
    Coupon minCoupon = new Coupon();
    minCoupon.setCode("MIN30");
    minCoupon.setMinimumOrderAmount(new BigDecimal("30.00"));
    minCoupon.setActive(true);

    assertThat(minCoupon.meetsMinimumAmount(new BigDecimal("20.00"))).isFalse();
    assertThat(minCoupon.meetsMinimumAmount(new BigDecimal("30.00"))).isTrue();

    assertThatThrownBy(() -> minCoupon.validateRules(new Date(), new BigDecimal("20.00")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no alcanza el monto mínimo de $30.00");
  }

  @Test
  public void validateRules_whenUsageLimitReached_shouldThrowException() {
    Coupon limitCoupon = new Coupon();
    limitCoupon.setCode("LIMIT10");
    limitCoupon.setMaxUsages(10);
    limitCoupon.setUsageCount(10);
    limitCoupon.setActive(true);

    assertThat(limitCoupon.isUsageLimitReached()).isTrue();
    assertThatThrownBy(() -> limitCoupon.validateRules(new Date(), new BigDecimal("50.00")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ha alcanzado su límite máximo de usos");
  }

  @Test
  public void validateRules_whenInactive_shouldThrowException() {
    Coupon inactive = new Coupon();
    inactive.setCode("OFF");
    inactive.setActive(false);

    assertThatThrownBy(() -> inactive.validateRules(new Date(), new BigDecimal("50.00")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no está activo");
  }

  @Test
  public void order_calculateTotalWithCoupon_shouldDeductDiscountFromSubTotal() {
    Taco taco = new Taco();
    taco.setName("Carnitas");
    taco.setPrice(new BigDecimal("10.00"));
    taco.setQuantity(2); // subtotal = 20.00

    TacoOrder order = new TacoOrder();
    order.addTaco(taco);
    order.setCouponCode("TACO10");
    order.setDiscount(new BigDecimal("2.00"));

    BigDecimal total = order.calculateTotal();

    assertThat(order.getSubTotal()).isEqualByComparingTo(new BigDecimal("20.00"));
    assertThat(order.getDiscount()).isEqualByComparingTo(new BigDecimal("2.00"));
    assertThat(total).isEqualByComparingTo(new BigDecimal("18.00"));
  }
}
