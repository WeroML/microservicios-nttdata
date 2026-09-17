package tacos.physics.rules;

import tacos.Ingredient;
import tacos.Taco;
import tacos.physics.PhysicsResult;
import tacos.physics.TacoPhysicsRule;

// Ejercicio 18: Taco Physics: reglas componibles de diseño
public class SolidFillingRule implements TacoPhysicsRule {

  @Override
  public PhysicsResult validate(Taco taco) {
    if (taco == null || taco.getIngredients() == null || taco.getIngredients().isEmpty()) {
      return PhysicsResult.invalid("Taco Physics Violation: A taco cannot be empty.");
    }

    boolean hasFilling = false;
    for (Ingredient ing : taco.getIngredients()) {
      if (ing != null && ing.getType() != null) {
        if (ing.getType() == Ingredient.Type.PROTEIN
            || ing.getType() == Ingredient.Type.CHEESE
            || ing.getType() == Ingredient.Type.VEGGIES) {
          hasFilling = true;
          break;
        }
      }
    }

    if (!hasFilling) {
      return PhysicsResult.invalid("Taco Physics Violation: A taco must contain at least one solid filling (PROTEIN, CHEESE, or VEGGIES).");
    }

    return PhysicsResult.valid();
  }
}
