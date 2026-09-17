package tacos.physics.rules;

import tacos.Taco;
import tacos.physics.PhysicsResult;
import tacos.physics.TacoPhysicsRule;

// Ejercicio 18: Taco Physics: reglas componibles de diseño
public class MaxCapacityRule implements TacoPhysicsRule {

  private final int maxIngredients;

  public MaxCapacityRule() {
    this(8);
  }

  public MaxCapacityRule(int maxIngredients) {
    this.maxIngredients = maxIngredients;
  }

  @Override
  public PhysicsResult validate(Taco taco) {
    if (taco == null || taco.getIngredients() == null) {
      return PhysicsResult.valid();
    }

    int count = taco.getIngredients().size();
    if (count > maxIngredients) {
      return PhysicsResult.invalid("Taco Physics Violation: Structural capacity exceeded! A taco can hold at most "
          + maxIngredients + " ingredients before tearing or overflowing (found " + count + ").");
    }

    return PhysicsResult.valid();
  }

  public int getMaxIngredients() {
    return maxIngredients;
  }
}
