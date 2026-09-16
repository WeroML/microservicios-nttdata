package tacos.web.api;

import java.util.Date;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.data.IngredientRepository;
import tacos.data.PaymentMethodRepository;
import tacos.data.UserRepository;

@Service
public class EmailOrderService {

  private UserRepository userRepo;
  private IngredientRepository ingredientRepo;
  private PaymentMethodRepository paymentMethodRepo;

  public EmailOrderService(UserRepository userRepo, IngredientRepository ingredientRepo,
      PaymentMethodRepository paymentMethodRepo) {
    this.userRepo = userRepo;
    this.ingredientRepo = ingredientRepo;
    this.paymentMethodRepo = paymentMethodRepo;
  }

  //Ejercicio 6: Convertir órdenes de correo sin carreras ni nulls sorpresa
  public Mono<TacoOrder> convertEmailOrderToDomainOrder(Mono<EmailOrder> emailOrder) {
    return emailOrder.flatMap(eOrder -> 
      userRepo.findByEmail(eOrder.getEmail())
          //Aquí se maneja el caso en que no se encuentra el usuario para el email de la orden, lanzando un error con un mensaje claro
          .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado para el email de la orden")))
          .flatMap(user -> 
              paymentMethodRepo.findByUserId(user.getId())
                  //Aquí también se maneja el caso en que no se encuentra un método de pago para el usuario, lanzando un error con un mensaje claro
                  .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "El usuario no tiene método de pago registrado")))
                  .flatMap(paymentMethod -> {
                    TacoOrder order = new TacoOrder();
                    order.setUser(user);
                    order.setPaymentToken(paymentMethod.getPaymentToken());
                    order.setCcExpiration(paymentMethod.getCcExpiration());
                    order.setLast4(paymentMethod.getLast4());
                    order.setDeliveryName(user.getFullname());
                    order.setDeliveryStreet(user.getStreet());
                    order.setDeliveryCity(user.getCity());
                    order.setDeliveryState(user.getState());
                    order.setDeliveryZip(user.getZip());
                    order.setPlacedAt(new Date());

                    // Quitamos el "for" y usamos flatMap para procesar los tacos de manera reactiva, evitando condiciones de carrera y nulls sorpresa
                    return Flux.fromIterable(eOrder.getTacos())
                        .flatMap(emailTaco -> 
                            Flux.fromIterable(emailTaco.getIngredients())
                                .flatMap(ingredientRepo::findById)
                                .collectList()
                                .map(ingredients -> {
                                  Taco taco = new Taco();
                                  taco.setName(emailTaco.getName());
                                  taco.setIngredients(ingredients);
                                  return taco;
                                })
                        )
                        .collectList()
                        .map(tacos -> {
                          for (Taco taco : tacos) {
                            order.addTaco(taco);
                          }
                          return order;
                        });
                  })
          )
    );
  }
}