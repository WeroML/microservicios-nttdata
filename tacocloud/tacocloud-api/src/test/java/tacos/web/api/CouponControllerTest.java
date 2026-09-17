package tacos.web.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import tacos.web.api.CouponEngine.CouponValidationResult;

// Ejercicio 15: Motor de cupones con reglas y fecha de expiración
public class CouponControllerTest {

  @Test
  public void validateCoupon_whenValid_shouldReturn200AndDetails() {
    CouponEngine couponEngine = Mockito.mock(CouponEngine.class);

    CouponValidationResult result = new CouponValidationResult(
        "TACO10", "10% de descuento", "PERCENTAGE", new BigDecimal("10"),
        new BigDecimal("5.00"), true, "Cupón válido y aplicable");

    when(couponEngine.validateCoupon(eq("TACO10"), eq(new BigDecimal("50.00")), any(Date.class)))
        .thenReturn(Mono.just(result));

    WebTestClient client = WebTestClient.bindToController(new CouponController(couponEngine)).build();

    client.get()
        .uri("/api/coupons/TACO10?orderAmount=50.00")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectBody()
          .jsonPath("$.code").isEqualTo("TACO10")
          .jsonPath("$.valid").isEqualTo(true)
          .jsonPath("$.calculatedDiscount").isEqualTo(5.00);
  }

  @Test
  public void validateCoupon_whenNotFound_shouldReturn404() {
    CouponEngine couponEngine = Mockito.mock(CouponEngine.class);

    when(couponEngine.validateCoupon(eq("UNKNOWN"), any(), any(Date.class)))
        .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Cupón no encontrado")));

    WebTestClient client = WebTestClient.bindToController(new CouponController(couponEngine)).build();

    client.get()
        .uri("/api/coupons/UNKNOWN")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isNotFound();
  }

  @Test
  public void validateCoupon_whenExpired_shouldReturn400() {
    CouponEngine couponEngine = Mockito.mock(CouponEngine.class);

    when(couponEngine.validateCoupon(eq("EXPIRED"), any(), any(Date.class)))
        .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cupón ha expirado")));

    WebTestClient client = WebTestClient.bindToController(new CouponController(couponEngine)).build();

    client.get()
        .uri("/api/coupons/EXPIRED")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isBadRequest();
  }
}
