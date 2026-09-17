package tacos;

// Ejercicio 17: Etiquetas dietarias, alérgenos y nivel de picante
public enum Allergen {
  GLUTEN("Gluten"),
  DAIRY("Lácteos"),
  EGGS("Huevos"),
  SOY("Soya"),
  PEANUTS("Cacahuates / Maní"),
  TREE_NUTS("Nueces de árbol"),
  FISH("Pescado"),
  SHELLFISH("Mariscos"),
  SESAME("Ajonjolí / Sésamo");

  private final String displayName;

  Allergen(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
