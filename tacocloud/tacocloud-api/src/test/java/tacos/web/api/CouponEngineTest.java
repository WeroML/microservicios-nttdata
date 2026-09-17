package tacos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tacos.Coupon;
import tacos.Coupon.DiscountType;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.data.CouponRepository;
import tacos.web.api.CouponEngine.CouponValidationResult;

// Ejercicio 15: Motor de cupones con reglas y fecha de expiración
public class CouponEngineTest {

  private CouponRepository couponRepo;
  private CouponEngine couponEngine;

  @BeforeEach
  public void setUp() {
    couponRepo = Mockito.mock(CouponRepository.class);
    couponEngine = new CouponEngine(couponRepo);
  }

  @Test
  public void applyCoupon_whenValidPercentageCoupon_shouldApplyDiscountAndIncrementUsage() {
    Date futureDate = new Date(System.currentTimeMillis() + 864000000L); // 10 días en el futuro
    Coupon coupon = new Coupon("TACO20", "20% off", DiscountType.PERCENTAGE, new BigDecimal("20"),
        null, null, futureDate, 100, true);
    coupon.setUsageCount(0);

    when(couponRepo.findByCodeIgnoreCase("TACO20")).thenReturn(Mono.just(coupon));
    when(couponRepo.save(any(Coupon.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    Taco taco = new Taco();
    taco.setPrice(new BigDecimal("50.00"));
    taco.setQuantity(1);

    TacoOrder order = new TacoOrder();
    order.addTaco(taco);
    order.setCouponCode("TACO20");
    order.calculateTotal(); // subTotal = 50.00

    StepVerifier.create(couponEngine.applyCoupon(order, new Date()))
        .assertNext(updatedOrder -> {
          assertThat(updatedOrder.getSubTotal()).isEqualByComparingTo(new BigDecimal("50.00"));
          assertThat(updatedOrder.getDiscount()).isEqualByComparingTo(new BigDecimal("10.00"));
          assertThat(updatedOrder.getTotal()).isEqualByComparingTo(new BigDecimal("40.00"));
          assertThat(coupon.getUsageCount()).isEqualTo(1);
        })
        .verifyComplete();

    verify(couponRepo).save(coupon);
  }

  @Test
  public void applyCoupon_whenValidFixedAmountCoupon_shouldApplyDiscount() {
    Date futureDate = new Date(System.currentTimeMillis() + 864000000L);
    Coupon coupon = new Coupon("FIVEOFF", "$5 off", DiscountType.FIXED_AMOUNT, new BigDecimal("5.00"),
        new BigDecimal("15.00"), null, futureDate, null, true);

    when(couponRepo.findByCodeIgnoreCase("FIVEOFF")).thenReturn(Mono.just(coupon));
    when(couponRepo.save(any(Coupon.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    Taco taco = new Taco();
    taco.setPrice(new BigDecimal("10.00"));
    taco.setQuantity(2); // subTotal = 20.00

    TacoOrder order = new TacoOrder();
    order.addTaco(taco);
    order.setCouponCode("FIVEOFF");
    order.calculateTotal();

    StepVerifier.create(couponEngine.applyCoupon(order, new Date()))
        .assertNext(updatedOrder -> {
          assertThat(updatedOrder.getSubTotal()).isEqualByComparingTo(new BigDecimal("20.00"));
          assertThat(updatedOrder.getDiscount()).isEqualByComparingTo(new BigDecimal("5.00"));
          assertThat(updatedOrder.getTotal()).isEqualByComparingTo(new BigDecimal("15.00"));
        })
        .verifyComplete();
  }

  @Test
  public void applyCoupon_whenExpired_shouldReturn400BadRequest() {
    Date pastDate = new Date(System.currentTimeMillis() - 864000000L); // 10 días atrás
    Coupon expired = new Coupon("EXPIRED", "Expired coupon", DiscountType.PERCENTAGE, new BigDecimal("10"),
        null, null, pastDate, null, true);

    when(couponRepo.findByCodeIgnoreCase("EXPIRED")).thenReturn(Mono.just(expired));

    Taco taco = new Taco();
    taco.setPrice(new BigDecimal("20.00"));
    taco.setQuantity(1);

    TacoOrder order = new TacoOrder();
    order.addTaco(taco);
    order.setCouponCode("EXPIRED");
    order.calculateTotal();

    StepVerifier.create(couponEngine.applyCoupon(order, new Date()))
        .expectErrorMatches(error -> error instanceof ResponseStatusException
            && ((ResponseStatusException) error).getStatus() == HttpStatus.BAD_REQUEST
            && error.getMessage().contains("ha expirado"))
        .verify();
  }

  @Test
  public void applyCoupon_whenOrderBelowMinimumAmount_shouldReturn400BadRequest() {
    Date futureDate = new Date(System.currentTimeMillis() + 864000000L);
    Coupon coupon = new Coupon("MIN30", "Min $30 order", DiscountType.FIXED_AMOUNT, new BigDecimal("5.00"),
        new BigDecimal("30.00"), null, futureDate, null, true);

    when(couponRepo.findByCodeIgnoreCase("MIN30")).thenReturn(Mono.just(coupon));

    Taco taco = new Taco();
    taco.setPrice(new BigDecimal("12.00"));
    taco.setQuantity(1); // subTotal = 12.00 < 30.00

    TacoOrder order = new TacoOrder();
    order.addTaco(taco);
    order.setCouponCode("MIN30");
    order.calculateTotal();

    StepVerifier.create(couponEngine.applyCoupon(order, new Date()))
        .expectErrorMatches(error -> error instanceof ResponseStatusException
            && ((ResponseStatusException) error).getStatus() == HttpStatus.BAD_REQUEST
            && error.getMessage().contains("no alcanza el monto mínimo"))
        .verify();
  }

  @Test
  public void applyCoupon_whenNonExistentCoupon_shouldReturn400BadRequest() {
    when(couponRepo.findByCodeIgnoreCase("NOTFOUND")).thenReturn(Mono.empty());

    Taco taco = new Taco();
    taco.setPrice(new BigDecimal("20.00"));
    taco.setQuantity(1);

    TacoOrder order = new TacoOrder();
    order.addTaco(taco);
    order.setCouponCode("NOTFOUND");
    order.calculateTotal();

    StepVerifier.create(couponEngine.applyCoupon(order, new Date()))
        .expectErrorMatches(error -> error instanceof ResponseStatusException
            && ((ResponseStatusException) error).getStatus() == HttpStatus.BAD_REQUEST
            && error.getMessage().contains("Cupón no válido o inexistente"))
        .verify();
  }

  @Test
  public void validateCoupon_whenValid_shouldReturnValidationResult() {
    Date futureDate = new Date(System.currentTimeMillis() + 864000000L);
    Coupon coupon = new Coupon("TACO15", "15% off", DiscountType.PERCENTAGE, new BigDecimal("15"),
        new BigDecimal("20.00"), null, futureDate, null, true);

    when(couponRepo.findByCodeIgnoreCase("TACO15")).thenReturn(Mono.just(coupon));

    StepVerifier.create(couponEngine.validateCoupon("TACO15", new BigDecimal("40.00"), new Date()))
        .assertNext(result -> {
          assertThat(result.isValid()).isTrue();
          assertThat(result.getCode()).isEqualTo("TACO15");
          assertThat(result.getCalculatedDiscount()).isEqualByComparingTo(new BigDecimal("6.00"));
        })
        .verifyComplete();
  }
}
