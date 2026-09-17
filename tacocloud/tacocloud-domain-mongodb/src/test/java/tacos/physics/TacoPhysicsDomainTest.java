package tacos.physics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tacos.Ingredient;
import tacos.Ingredient.Type;
import tacos.Taco;
import tacos.physics.rules.BaseFoundationRule;
import tacos.physics.rules.MaxCapacityRule;
import tacos.physics.rules.MoistureBalanceRule;
import tacos.physics.rules.SolidFillingRule;

// Ejercicio 18: Taco Physics: reglas componibles de diseño
public class TacoPhysicsDomainTest {

  private Ingredient tortilla;
  private Ingredient beef;
  private Ingredient cheese;
  private Ingredient salsa;
  private Ingredient sourCream;
  private Ingredient habanero;

  @BeforeEach
  public void setUp() {
    tortilla = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP, BigDecimal.ONE, true, 10);
    beef = new Ingredient("GRBF", "Ground Beef", Type.PROTEIN, BigDecimal.ONE, true, 10);
    cheese = new Ingredient("CHED", "Cheddar", Type.CHEESE, BigDecimal.ONE, true, 10);
    salsa = new Ingredient("SLSA", "Salsa", Type.SAUCE, BigDecimal.ONE, true, 10);
    sourCream = new Ingredient("SRCR", "Sour Cream", Type.SAUCE, BigDecimal.ONE, true, 10);
    habanero = new Ingredient("HBNR", "Habanero Sauce", Type.SAUCE, BigDecimal.ONE, true, 10);
  }

  @Test
  public void baseFoundationRule_whenNoWrap_shouldFail() {
    Taco taco = new Taco();
    taco.setName("Floating Taco");
    taco.setIngredients(Arrays.asList(beef, cheese)); // Sin tortilla

    BaseFoundationRule rule = new BaseFoundationRule();
    PhysicsResult result = rule.validate(taco);

    assertThat(result.isValid()).isFalse();
    assertThat(result.getReason()).contains("Missing base");
  }

  @Test
  public void baseFoundationRule_whenTooManyWraps_shouldFail() {
    Taco taco = new Taco();
    taco.setName("Tortilla Tower");
    taco.setIngredients(Arrays.asList(tortilla, tortilla, tortilla, beef)); // 3 tortillas

    BaseFoundationRule rule = new BaseFoundationRule();
    PhysicsResult result = rule.validate(taco);

    assertThat(result.isValid()).isFalse();
    assertThat(result.getReason()).contains("Exceeded maximum tortilla limit");
  }

  @Test
  public void maxCapacityRule_whenExceedsLimit_shouldFail() {
    Taco taco = new Taco();
    taco.setName("Mega Taco");
    List<Ingredient> ingredients = new ArrayList<>();
    ingredients.add(tortilla);
    for (int i = 0; i < 8; i++) {
      ingredients.add(beef);
    }
    taco.setIngredients(ingredients); // 9 ingredientes total

    MaxCapacityRule rule = new MaxCapacityRule(8);
    PhysicsResult result = rule.validate(taco);

    assertThat(result.isValid()).isFalse();
    assertThat(result.getReason()).contains("Structural capacity exceeded");
  }

  @Test
  public void moistureBalanceRule_whenExcessiveSauces_shouldFail() {
    Taco taco = new Taco();
    taco.setName("Soggy Taco");
    taco.setIngredients(Arrays.asList(tortilla, beef, salsa, sourCream, habanero)); // 3 salsas

    MoistureBalanceRule rule = new MoistureBalanceRule(2);
    PhysicsResult result = rule.validate(taco);

    assertThat(result.isValid()).isFalse();
    assertThat(result.getReason()).contains("Moisture balance exceeded");
  }

  @Test
  public void moistureBalanceRule_whenSauceWithoutSolidFilling_shouldFail() {
    Taco taco = new Taco();
    taco.setName("Soup Wrap");
    taco.setIngredients(Arrays.asList(tortilla, salsa)); // Solo tortilla y salsa

    MoistureBalanceRule rule = new MoistureBalanceRule(2);
    PhysicsResult result = rule.validate(taco);

    assertThat(result.isValid()).isFalse();
    assertThat(result.getReason()).contains("Cannot add sauce to an empty tortilla without solid fillings");
  }

  @Test
  public void solidFillingRule_whenNoFilling_shouldFail() {
    Taco taco = new Taco();
    taco.setName("Empty Wrap");
    taco.setIngredients(Arrays.asList(tortilla));

    SolidFillingRule rule = new SolidFillingRule();
    assertThat(rule.validate(taco).isValid()).isFalse();

    taco.setIngredients(Arrays.asList(tortilla, beef));
    assertThat(rule.validate(taco).isValid()).isTrue();
  }

  @Test
  public void composableRules_andComposition_shouldRequireAll() {
    TacoPhysicsRule composite = new BaseFoundationRule()
        .and(new MaxCapacityRule(5))
        .and(new MoistureBalanceRule(1));

    Taco validTaco = new Taco();
    validTaco.setName("Valid Taco");
    validTaco.setIngredients(Arrays.asList(tortilla, beef, cheese, salsa));

    assertThat(composite.validate(validTaco).isValid()).isTrue();

    // Viola el límite de 1 salsa
    Taco tooWetTaco = new Taco();
    tooWetTaco.setName("Too Wet Taco");
    tooWetTaco.setIngredients(Arrays.asList(tortilla, beef, salsa, sourCream));

    PhysicsResult wetResult = composite.validate(tooWetTaco);
    assertThat(wetResult.isValid()).isFalse();
    assertThat(wetResult.getReason()).contains("Moisture balance exceeded");
  }

  @Test
  public void composableRules_orComposition_shouldPassIfEitherMatches() {
    TacoPhysicsRule rule = new SolidFillingRule()
        .or(TacoPhysicsRule.fromPredicate(taco -> "CheeseSpecial".equals(taco.getName()), "Must be CheeseSpecial"));

    Taco cheeseSpecial = new Taco();
    cheeseSpecial.setName("CheeseSpecial");
    cheeseSpecial.setIngredients(Arrays.asList(tortilla));

    assertThat(rule.validate(cheeseSpecial).isValid()).isTrue();
  }

  @Test
  public void taco_validatePhysics_standardPreset_shouldWork() {
    Taco taco = new Taco();
    taco.setName("Classic Taco");
    taco.setIngredients(Arrays.asList(tortilla, beef, cheese, salsa));

    PhysicsResult res = taco.validatePhysics();
    assertThat(res.isValid()).isTrue();
  }
}
