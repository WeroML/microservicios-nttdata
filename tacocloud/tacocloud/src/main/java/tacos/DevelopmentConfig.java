package tacos;

import java.math.BigDecimal;
import java.util.Arrays;

import java.util.Date;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import tacos.Coupon;
import tacos.Coupon.DiscountType;
import tacos.Ingredient.Type;
import tacos.data.CouponRepository;
import tacos.data.IngredientRepository;
import tacos.data.PaymentMethodRepository;
import tacos.data.TacoRepository;
import tacos.data.UserRepository;

@Profile("!prod")
@Configuration
public class DevelopmentConfig {

  @Bean
  public CommandLineRunner dataLoader(IngredientRepository repo,
        UserRepository userRepo, PasswordEncoder encoder, TacoRepository tacoRepo,
        PaymentMethodRepository paymentMethodRepo, CouponRepository couponRepo) { // user repo for ease of testing with a built-in user
    
    return new CommandLineRunner() {
      @Override
      public void run(String... args) throws Exception {
        // Ejercicio 13: Catálogo con precio, disponibilidad y stock
        Ingredient flourTortilla = saveAnIngredient("FLTO", "Flour Tortilla", Type.WRAP, new BigDecimal("0.75"), true, 100);
        Ingredient cornTortilla = saveAnIngredient("COTO", "Corn Tortilla", Type.WRAP, new BigDecimal("0.70"), true, 100);
        Ingredient groundBeef = saveAnIngredient("GRBF", "Ground Beef", Type.PROTEIN, new BigDecimal("2.50"), true, 80);
        Ingredient carnitas = saveAnIngredient("CARN", "Carnitas", Type.PROTEIN, new BigDecimal("2.80"), true, 75);
        Ingredient tomatoes = saveAnIngredient("TMTO", "Diced Tomatoes", Type.VEGGIES, new BigDecimal("0.50"), true, 120);
        Ingredient lettuce = saveAnIngredient("LETC", "Lettuce", Type.VEGGIES, new BigDecimal("0.45"), true, 110);
        Ingredient cheddar = saveAnIngredient("CHED", "Cheddar", Type.CHEESE, new BigDecimal("0.90"), true, 90);
        Ingredient jack = saveAnIngredient("JACK", "Monterrey Jack", Type.CHEESE, new BigDecimal("0.95"), true, 85);
        Ingredient salsa = saveAnIngredient("SLSA", "Salsa", Type.SAUCE, new BigDecimal("0.60"), true, 150);
        Ingredient sourCream = saveAnIngredient("SRCR", "Sour Cream", Type.SAUCE, new BigDecimal("0.65"), true, 140);
        
//        UserUDT u = new UserUDT(username, fullname, phoneNumber)
        
        userRepo.save(new User("habuma", encoder.encode("password"), 
              "Craig Walls", "123 North Street", "Cross Roads", "TX", 
              "76227", "123-123-1234", "craig@habuma.com"))
          .subscribe(user -> {
              paymentMethodRepo.save(new PaymentMethod(user, "tok_visa_4111_default", "10/25", "4111")).subscribe();
          });        
        
        Taco taco1 = new Taco();
        taco1.setId("TACO1");
        taco1.setName("Carnivore");
        taco1.setPrice(new BigDecimal("6.99"));
        taco1.setAvailable(true);
        taco1.setStock(50);
        taco1.setIngredients(Arrays.asList(flourTortilla, groundBeef, carnitas, sourCream, salsa, cheddar));
        tacoRepo.save(taco1).subscribe();

        Taco taco2 = new Taco();
        taco2.setId("TACO2");
        taco2.setName("Bovine Bounty");
        taco2.setPrice(new BigDecimal("5.99"));
        taco2.setAvailable(true);
        taco2.setStock(40);
        taco2.setIngredients(Arrays.asList(cornTortilla, groundBeef, cheddar, jack, sourCream));
        tacoRepo.save(taco2).subscribe();

        Taco taco3 = new Taco();
        taco3.setId("TACO3");
        taco3.setName("Veg-Out");
        taco3.setPrice(new BigDecimal("4.99"));
        taco3.setAvailable(true);
        taco3.setStock(60);
        taco3.setIngredients(Arrays.asList(flourTortilla, cornTortilla, tomatoes, lettuce, salsa));
        tacoRepo.save(taco3).subscribe();

        // Ejercicio 15: Motor de cupones con reglas y fecha de expiración
        Date futureExpiration = new Date(System.currentTimeMillis() + 315360000000L); // 10 años en el futuro
        Date pastExpiration = new Date(System.currentTimeMillis() - 86400000L); // ayer

        Coupon taco10 = new Coupon("TACO10", "10% de descuento en tu orden", DiscountType.PERCENTAGE,
            new BigDecimal("10"), null, null, futureExpiration, 1000, true);
        couponRepo.save(taco10).subscribe();

        Coupon fiveOff = new Coupon("FIVEOFF", "$5 de descuento en pedidos mayores a $15", DiscountType.FIXED_AMOUNT,
            new BigDecimal("5.00"), new BigDecimal("15.00"), null, futureExpiration, 500, true);
        couponRepo.save(fiveOff).subscribe();

        Coupon expired = new Coupon("EXPIRED20", "20% caducado", DiscountType.PERCENTAGE,
            new BigDecimal("20"), null, null, pastExpiration, null, true);
        couponRepo.save(expired).subscribe();

        Coupon limit1 = new Coupon("LIMIT1", "15% cupón de un solo uso agotado", DiscountType.PERCENTAGE,
            new BigDecimal("15"), null, null, futureExpiration, 1, true);
        limit1.setUsageCount(1);
        couponRepo.save(limit1).subscribe();

      }

      private Ingredient saveAnIngredient(String id, String name, Type type, BigDecimal price, boolean available, int stock) {
        Ingredient ingredient = new Ingredient(id, name, type, price, available, stock);
        repo.save(ingredient).subscribe();
        return ingredient;
      }

      private Ingredient saveAnIngredient(String id, String name, Type type) {
        return saveAnIngredient(id, name, type, BigDecimal.ZERO, true, 0);
      }
    };
  }
  
}
