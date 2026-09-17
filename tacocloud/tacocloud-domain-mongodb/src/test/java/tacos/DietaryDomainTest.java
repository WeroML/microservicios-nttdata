package tacos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import tacos.Ingredient.Type;

// Ejercicio 17: Etiquetas dietarias, alérgenos y nivel de picante
public class DietaryDomainTest {

  @Test
  public void ingredient_shouldStoreAndCheckDietaryLabelsAndAllergens() {
    Set<DietaryLabel> labels = new HashSet<>(Arrays.asList(DietaryLabel.VEGAN, DietaryLabel.VEGETARIAN));
    Set<Allergen> allergens = new HashSet<>(Collections.singletonList(Allergen.GLUTEN));

    Ingredient flourTortilla = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP,
        new BigDecimal("0.75"), true, 100, labels, allergens, SpiceLevel.NONE);

    assertThat(flourTortilla.hasDietaryLabel(DietaryLabel.VEGAN)).isTrue();
    assertThat(flourTortilla.hasDietaryLabel(DietaryLabel.KETO)).isFalse();
    assertThat(flourTortilla.hasAllergen(Allergen.GLUTEN)).isTrue();
    assertThat(flourTortilla.hasAllergen(Allergen.DAIRY)).isFalse();
    assertThat(flourTortilla.containsAnyAllergen(Arrays.asList(Allergen.DAIRY, Allergen.GLUTEN))).isTrue();
    assertThat(flourTortilla.containsAnyAllergen(Collections.singletonList(Allergen.PEANUTS))).isFalse();
    assertThat(flourTortilla.getSpiceLevel()).isEqualTo(SpiceLevel.NONE);
  }

  @Test
  public void taco_computeAllergens_shouldReturnUnionOfAllIngredientAllergens() {
    Ingredient flourTortilla = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP,
        new BigDecimal("0.75"), true, 100,
        new HashSet<>(Arrays.asList(DietaryLabel.VEGAN, DietaryLabel.VEGETARIAN)),
        new HashSet<>(Collections.singletonList(Allergen.GLUTEN)),
        SpiceLevel.NONE);

    Ingredient cheddar = new Ingredient("CHED", "Cheddar", Type.CHEESE,
        new BigDecimal("0.90"), true, 50,
        new HashSet<>(Arrays.asList(DietaryLabel.VEGETARIAN, DietaryLabel.KETO)),
        new HashSet<>(Collections.singletonList(Allergen.DAIRY)),
        SpiceLevel.NONE);

    Ingredient groundBeef = new Ingredient("GRBF", "Ground Beef", Type.PROTEIN,
        new BigDecimal("2.50"), true, 50,
        new HashSet<>(Arrays.asList(DietaryLabel.KETO, DietaryLabel.GLUTEN_FREE)),
        Collections.emptySet(),
        SpiceLevel.NONE);

    Taco taco = new Taco();
    taco.setName("Beef & Cheese Wrap");
    taco.setIngredients(Arrays.asList(flourTortilla, cheddar, groundBeef));

    Set<Allergen> computedAllergens = taco.computeAllergens();
    assertThat(computedAllergens).containsExactlyInAnyOrder(Allergen.GLUTEN, Allergen.DAIRY);
    assertThat(taco.hasAllergen(Allergen.GLUTEN)).isTrue();
    assertThat(taco.hasAllergen(Allergen.DAIRY)).isTrue();
    assertThat(taco.hasAllergen(Allergen.SOY)).isFalse();
  }

  @Test
  public void taco_computeSpiceLevel_shouldReturnMaximumLevelAmongIngredients() {
    Ingredient cornTortilla = new Ingredient("COTO", "Corn Tortilla", Type.WRAP,
        new BigDecimal("0.70"), true, 100, Collections.emptySet(), Collections.emptySet(), SpiceLevel.NONE);

    Ingredient salsa = new Ingredient("SLSA", "Salsa", Type.SAUCE,
        new BigDecimal("0.60"), true, 100, Collections.emptySet(), Collections.emptySet(), SpiceLevel.MEDIUM);

    Ingredient habanero = new Ingredient("HBNR", "Habanero Sauce", Type.SAUCE,
        new BigDecimal("0.80"), true, 50, Collections.emptySet(), Collections.emptySet(), SpiceLevel.EXTRA_HOT);

    Taco mildTaco = new Taco();
    mildTaco.setName("Mild Taco");
    mildTaco.setIngredients(Arrays.asList(cornTortilla, salsa));
    assertThat(mildTaco.computeSpiceLevel()).isEqualTo(SpiceLevel.MEDIUM);

    Taco hotTaco = new Taco();
    hotTaco.setName("Inferno Taco");
    hotTaco.setIngredients(Arrays.asList(cornTortilla, salsa, habanero));
    assertThat(hotTaco.computeSpiceLevel()).isEqualTo(SpiceLevel.EXTRA_HOT);
  }

  @Test
  public void taco_computeDietaryLabels_shouldVerifyStrictComplianceForAllIngredients() {
    Ingredient cornTortilla = new Ingredient("COTO", "Corn Tortilla", Type.WRAP,
        new BigDecimal("0.70"), true, 100,
        new HashSet<>(Arrays.asList(DietaryLabel.VEGAN, DietaryLabel.VEGETARIAN, DietaryLabel.GLUTEN_FREE, DietaryLabel.DAIRY_FREE)),
        Collections.emptySet(), SpiceLevel.NONE);

    Ingredient tomatoes = new Ingredient("TMTO", "Tomatoes", Type.VEGGIES,
        new BigDecimal("0.50"), true, 100,
        new HashSet<>(Arrays.asList(DietaryLabel.VEGAN, DietaryLabel.VEGETARIAN, DietaryLabel.GLUTEN_FREE, DietaryLabel.DAIRY_FREE)),
        Collections.emptySet(), SpiceLevel.NONE);

    Ingredient lettuce = new Ingredient("LETC", "Lettuce", Type.VEGGIES,
        new BigDecimal("0.45"), true, 100,
        new HashSet<>(Arrays.asList(DietaryLabel.VEGAN, DietaryLabel.VEGETARIAN, DietaryLabel.GLUTEN_FREE, DietaryLabel.DAIRY_FREE)),
        Collections.emptySet(), SpiceLevel.NONE);

    Taco veganTaco = new Taco();
    veganTaco.setName("Pure Veggie Taco");
    veganTaco.setIngredients(Arrays.asList(cornTortilla, tomatoes, lettuce));

    Set<DietaryLabel> labels = veganTaco.computeDietaryLabels();
    assertThat(labels).contains(DietaryLabel.VEGAN, DietaryLabel.VEGETARIAN, DietaryLabel.GLUTEN_FREE, DietaryLabel.DAIRY_FREE);
    assertThat(veganTaco.hasDietaryLabel(DietaryLabel.VEGAN)).isTrue();
    assertThat(veganTaco.hasDietaryLabel(DietaryLabel.GLUTEN_FREE)).isTrue();

    // Si agregamos carne, deja de ser vegano y vegetariano
    Ingredient carnitas = new Ingredient("CARN", "Carnitas", Type.PROTEIN,
        new BigDecimal("2.80"), true, 50,
        new HashSet<>(Arrays.asList(DietaryLabel.KETO, DietaryLabel.GLUTEN_FREE, DietaryLabel.DAIRY_FREE)),
        Collections.emptySet(), SpiceLevel.MILD);

    Taco carnivoreTaco = new Taco();
    carnivoreTaco.setName("Carnivore Taco");
    carnivoreTaco.setIngredients(Arrays.asList(cornTortilla, carnitas));

    Set<DietaryLabel> carnivoreLabels = carnivoreTaco.computeDietaryLabels();
    assertThat(carnivoreLabels).doesNotContain(DietaryLabel.VEGAN, DietaryLabel.VEGETARIAN);
    assertThat(carnivoreLabels).contains(DietaryLabel.GLUTEN_FREE, DietaryLabel.DAIRY_FREE);
    assertThat(carnivoreTaco.hasDietaryLabel(DietaryLabel.VEGAN)).isFalse();
  }

  @Test
  public void taco_updateDietaryAndAllergenInfo_shouldUpdateAllFields() {
    Ingredient cornTortilla = new Ingredient("COTO", "Corn Tortilla", Type.WRAP,
        new BigDecimal("0.70"), true, 100,
        new HashSet<>(Arrays.asList(DietaryLabel.VEGAN, DietaryLabel.VEGETARIAN, DietaryLabel.GLUTEN_FREE, DietaryLabel.DAIRY_FREE)),
        Collections.emptySet(), SpiceLevel.NONE);

    Ingredient salsa = new Ingredient("SLSA", "Salsa", Type.SAUCE,
        new BigDecimal("0.60"), true, 100,
        new HashSet<>(Arrays.asList(DietaryLabel.VEGAN, DietaryLabel.VEGETARIAN, DietaryLabel.GLUTEN_FREE, DietaryLabel.DAIRY_FREE)),
        Collections.emptySet(), SpiceLevel.HOT);

    Taco taco = new Taco();
    taco.setName("Spicy Corn Taco");
    taco.setIngredients(Arrays.asList(cornTortilla, salsa));

    taco.updateDietaryAndAllergenInfo();

    assertThat(taco.getAllergens()).isEmpty();
    assertThat(taco.getSpiceLevel()).isEqualTo(SpiceLevel.HOT);
    assertThat(taco.getDietaryLabels()).contains(DietaryLabel.VEGAN, DietaryLabel.GLUTEN_FREE);
  }
}
