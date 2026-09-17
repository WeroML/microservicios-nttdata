package tacos.data;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.web.bind.annotation.CrossOrigin;

import reactor.core.publisher.Mono;
import tacos.Coupon;

// Ejercicio 15: Motor de cupones con reglas y fecha de expiración
@CrossOrigin(origins="http://localhost:8080")
public interface CouponRepository extends ReactiveCrudRepository<Coupon, String> {

  Mono<Coupon> findByCodeIgnoreCase(String code);

  Mono<Coupon> findByCode(String code);

}
