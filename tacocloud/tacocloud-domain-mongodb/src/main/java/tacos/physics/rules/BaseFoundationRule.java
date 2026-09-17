package tacos.physics.rules;

import tacos.Ingredient;
import tacos.Taco;
import tacos.physics.PhysicsResult;
import tacos.physics.TacoPhysicsRule;

// Ejercicio 18: Taco Physics: reglas componibles de diseño
public class BaseFoundationRule implements TacoPhysicsRule {

  private final int minWraps;
  private final int maxWraps;

  public BaseFoundationRule() {
    this(1, 2);
  }

  public BaseFoundationRule(int minWraps, int maxWraps) {
    this.minWraps = minWraps;
    this.maxWraps = maxWraps;
  }

  @Override
  public PhysicsResult validate(Taco taco) {
    if (taco == null || taco.getIngredients() == null || taco.getIngredients().isEmpty()) {
      return PhysicsResult.invalid("Taco Physics Violation: A taco cannot exist without ingredients (foundation required).");
    }

    int wrapCount = 0;
    for (Ingredient ing : taco.getIngredients()) {
      if (ing != null && ing.getType() == Ingredient.Type.WRAP) {
        wrapCount++;
      }
    }

    if (wrapCount < minWraps) {
      return PhysicsResult.invalid("Taco Physics Violation: A taco requires a foundation! Missing base (at least " + minWraps + " WRAP required). Without a tortilla, ingredients fall to the floor.");
    }

    if (wrapCount > maxWraps) {
      return PhysicsResult.invalid("Taco Physics Violation: Exceeded maximum tortilla limit (" + maxWraps + " WRAPs allowed, found " + wrapCount + "). A taco cannot be a stack of tortillas.");
    }

    return PhysicsResult.valid();
  }
}
