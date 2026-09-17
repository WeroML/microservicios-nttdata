package tacos;

// Ejercicio 17: Etiquetas dietarias, alérgenos y nivel de picante
public enum DietaryLabel {
  VEGAN("Vegano"),
  VEGETARIAN("Vegetariano"),
  GLUTEN_FREE("Sin Gluten"),
  DAIRY_FREE("Sin Lácteos"),
  KETO("Keto"),
  LOW_CARB("Bajo en Carbohidratos");

  private final String displayName;

  DietaryLabel(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
