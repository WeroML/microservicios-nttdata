package tacos.physics.rules;

import tacos.Ingredient;
import tacos.Taco;
import tacos.physics.PhysicsResult;
import tacos.physics.TacoPhysicsRule;

// Ejercicio 18: Taco Physics: reglas componibles de diseño
public class MoistureBalanceRule implements TacoPhysicsRule {

  private final int maxSauces;

  public MoistureBalanceRule() {
    this(2);
  }

  public MoistureBalanceRule(int maxSauces) {
    this.maxSauces = maxSauces;
  }

  @Override
  public PhysicsResult validate(Taco taco) {
    if (taco == null || taco.getIngredients() == null) {
      return PhysicsResult.valid();
    }

    int sauceCount = 0;
    int solidCount = 0;

    for (Ingredient ing : taco.getIngredients()) {
      if (ing != null && ing.getType() != null) {
        if (ing.getType() == Ingredient.Type.SAUCE) {
          sauceCount++;
        } else if (ing.getType() != Ingredient.Type.WRAP) {
          solidCount++;
        }
      }
    }

    if (sauceCount > maxSauces) {
      return PhysicsResult.invalid("Taco Physics Violation: Moisture balance exceeded! Maximum "
          + maxSauces + " sauces allowed (found " + sauceCount + "). Excessive liquid causes soggy tortilla structural failure.");
    }

    if (sauceCount > 0 && solidCount == 0) {
      return PhysicsResult.invalid("Taco Physics Violation: Cannot add sauce to an empty tortilla without solid fillings. Tortilla will dissolve into soup.");
    }

    return PhysicsResult.valid();
  }

  public int getMaxSauces() {
    return maxSauces;
  }
}
