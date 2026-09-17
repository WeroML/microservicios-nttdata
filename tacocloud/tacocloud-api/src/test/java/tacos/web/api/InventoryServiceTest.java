package tacos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tacos.Ingredient;
import tacos.Ingredient.Type;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.data.IngredientRepository;
import tacos.data.TacoRepository;

// Ejercicio 16: Reservar y liberar inventario sin vender aire
public class InventoryServiceTest {

  private IngredientRepository ingredientRepo;
  private TacoRepository tacoRepo;
  private InventoryService inventoryService;

  @BeforeEach
  public void setUp() {
    ingredientRepo = Mockito.mock(IngredientRepository.class);
    tacoRepo = Mockito.mock(TacoRepository.class);
    inventoryService = new InventoryService(ingredientRepo, tacoRepo);
  }

  @Test
  public void reserveInventory_whenStockSufficient_shouldDecrementStockAndSave() {
    Ingredient tortilla = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP, new BigDecimal("0.75"), true, 10);

    when(ingredientRepo.findById("FLTO")).thenReturn(Mono.just(tortilla));
    when(ingredientRepo.save(any(Ingredient.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    Taco taco = new Taco();
    taco.setIngredients(Collections.singletonList(tortilla));
    taco.setQuantity(3);

    TacoOrder order = new TacoOrder();
    order.addTaco(taco);

    StepVerifier.create(inventoryService.reserveInventory(order))
        .assertNext(resOrder -> {
          assertThat(tortilla.getStock()).isEqualTo(7);
          assertThat(tortilla.isAvailable()).isTrue();
        })
        .verifyComplete();

    verify(ingredientRepo).save(tortilla);
  }

  @Test
  public void reserveInventory_whenStockInsufficient_shouldReturn409ConflictWithoutSaving() {
    Ingredient carnitas = new Ingredient("CARN", "Carnitas", Type.PROTEIN, new BigDecimal("2.80"), true, 2);

    when(ingredientRepo.findById("CARN")).thenReturn(Mono.just(carnitas));

    Taco taco = new Taco();
    taco.setIngredients(Collections.singletonList(carnitas));
    taco.setQuantity(5); // Requiere 5 pero solo hay 2 disponibles

    TacoOrder order = new TacoOrder();
    order.addTaco(taco);

    StepVerifier.create(inventoryService.reserveInventory(order))
        .expectErrorMatches(error -> error instanceof ResponseStatusException
            && ((ResponseStatusException) error).getStatus() == HttpStatus.CONFLICT
            && error.getMessage().contains("Stock insuficiente para el ingrediente 'Carnitas'"))
        .verify();

    // Verificamos que NO se vendió aire y no se mutó/guardó en la base de datos
    verify(ingredientRepo, never()).save(any(Ingredient.class));
    assertThat(carnitas.getStock()).isEqualTo(2);
  }

  @Test
  public void reserveInventory_whenIngredientUnavailable_shouldReturn409Conflict() {
    Ingredient tomatoes = new Ingredient("TMTO", "Tomatoes", Type.VEGGIES, new BigDecimal("0.50"), false, 10);

    when(ingredientRepo.findById("TMTO")).thenReturn(Mono.just(tomatoes));

    Taco taco = new Taco();
    taco.setIngredients(Collections.singletonList(tomatoes));
    taco.setQuantity(1);

    TacoOrder order = new TacoOrder();
    order.addTaco(taco);

    StepVerifier.create(inventoryService.reserveInventory(order))
        .expectErrorMatches(error -> error instanceof ResponseStatusException
            && ((ResponseStatusException) error).getStatus() == HttpStatus.CONFLICT)
        .verify();

    verify(ingredientRepo, never()).save(any(Ingredient.class));
  }

  @Test
  public void releaseInventory_shouldIncrementStockAndRestoreAvailability() {
    Ingredient beef = new Ingredient("GRBF", "Ground Beef", Type.PROTEIN, new BigDecimal("2.50"), false, 0);

    when(ingredientRepo.findById("GRBF")).thenReturn(Mono.just(beef));
    when(ingredientRepo.save(any(Ingredient.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    Taco taco = new Taco();
    taco.setIngredients(Collections.singletonList(beef));
    taco.setQuantity(4);

    TacoOrder order = new TacoOrder();
    order.addTaco(taco);

    StepVerifier.create(inventoryService.releaseInventory(order))
        .verifyComplete();

    assertThat(beef.getStock()).isEqualTo(4);
    assertThat(beef.isAvailable()).isTrue();
    verify(ingredientRepo).save(beef);
  }
}
