package tacos.web.api;

import java.math.BigDecimal;
import java.util.Date;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import tacos.web.api.CouponEngine.CouponValidationResult;

// Ejercicio 15: Motor de cupones con reglas y fecha de expiración
@RestController
@RequestMapping(path="/api/coupons", produces="application/json")
@CrossOrigin(origins="http://localhost:8080")
public class CouponController {

  private final CouponEngine couponEngine;

  public CouponController(CouponEngine couponEngine) {
    this.couponEngine = couponEngine;
  }

  @GetMapping("/{code}")
  public Mono<CouponValidationResult> validateCoupon(
      @PathVariable("code") String code,
      @RequestParam(name="orderAmount", required=false, defaultValue="0.00") BigDecimal orderAmount) {
    return couponEngine.validateCoupon(code, orderAmount, new Date());
  }

}
