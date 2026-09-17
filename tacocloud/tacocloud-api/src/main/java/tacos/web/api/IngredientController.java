package tacos.web.api;

import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.data.IngredientRepository;

@RestController
@RequestMapping(path="/api/ingredients", produces="application/json")
@CrossOrigin(origins="http://localhost:8080")
public class IngredientController {

  private IngredientRepository repo;

  @Autowired
  public IngredientController(IngredientRepository repo) {
    this.repo = repo;
  }

  // Ejercicio 13: Catálogo con precio, disponibilidad y stock
  // Ejercicio 17: Etiquetas dietarias, alérgenos y nivel de picante
  @GetMapping
  public Flux<Ingredient> allIngredients(
      @RequestParam(name = "available", required = false) Boolean available,
      @RequestParam(name = "inStock", required = false) Boolean inStock,
      @RequestParam(name = "dietary", required = false) tacos.DietaryLabel dietary,
      @RequestParam(name = "excludeAllergen", required = false) tacos.Allergen excludeAllergen,
      @RequestParam(name = "maxSpice", required = false) tacos.SpiceLevel maxSpice) {
    Flux<Ingredient> flux;
    if (Boolean.TRUE.equals(available) && Boolean.TRUE.equals(inStock)) {
      flux = repo.findByAvailableTrueAndStockGreaterThan(0);
    } else if (Boolean.TRUE.equals(available)) {
      flux = repo.findByAvailableTrue();
    } else {
      flux = repo.findAll();
    }

    return flux.filter(ing -> {
      if (dietary != null && !ing.hasDietaryLabel(dietary)) {
        return false;
      }
      if (excludeAllergen != null && ing.hasAllergen(excludeAllergen)) {
        return false;
      }
      if (maxSpice != null && ing.getSpiceLevel() != null && ing.getSpiceLevel().getLevel() > maxSpice.getLevel()) {
        return false;
      }
      return true;
    });
  }

  // Ejercicio 13: Catálogo con precio, disponibilidad y stock
  // Ejercicio 17: Etiquetas dietarias, alérgenos y nivel de picante
  @PatchMapping(path = "/{id}", consumes = "application/json")
  public Mono<ResponseEntity<Ingredient>> patchIngredient(@PathVariable String id, @RequestBody Ingredient patch) {
    return repo.findById(id)
        .flatMap(ingredient -> {
          if (patch.getName() != null) {
            ingredient.setName(patch.getName());
          }
          if (patch.getType() != null) {
            ingredient.setType(patch.getType());
          }
          if (patch.getPrice() != null) {
            ingredient.setPrice(patch.getPrice());
          }
          if (patch.getAvailable() != null) {
            ingredient.setAvailable(patch.getAvailable());
          }
          if (patch.getStock() != null) {
            ingredient.setStock(patch.getStock());
          }
          if (patch.getDietaryLabels() != null) {
            ingredient.setDietaryLabels(patch.getDietaryLabels());
          }
          if (patch.getAllergens() != null) {
            ingredient.setAllergens(patch.getAllergens());
          }
          if (patch.getSpiceLevel() != null) {
            ingredient.setSpiceLevel(patch.getSpiceLevel());
          }
          return repo.save(ingredient);
        })
        .map(ResponseEntity::ok)
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "El ingrediente con el ID especificado no existe.")));
  }

  @GetMapping("/{id}")
  public Mono<Ingredient> byId(@PathVariable String id) {
    return repo.findById(id);
  }

  //Ejercicio 1: Actualizar un ingrediente sin perder el publisher
  @PutMapping("/{id}")
  //En lugar de retornar void, retornamos un Mono<Ingredient> para mantener el publisher y permitir la suscripción a la operación de actualización.
  public Mono<ResponseEntity<Ingredient>> updateIngredient(@PathVariable String id, @RequestBody Ingredient ingredient) {
    //1. Validamos que el id del ingrediente en la solicitud coincida con el id en la ruta. Si no coinciden, retornamos un error con el código de estado 400 Bad Request.
    if(!id.equals(ingredient.getId())) {
      //En lugar de retornar un error normal, retornamos un Mono.error con una excepción que indica que los IDs no coinciden. Esto permite que el flujo de datos continúe y se maneje adecuadamente en la suscripción.
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID del ingrediente en la solicitud no coincide con el ID en la ruta."));

    }

    //2. Validamos que exista un ingrediente con el ID proporcionado en la ruta. Si no existe, retornamos un error con el código de estado 404 Not Found.
    return repo.findById(id)
        //Si existe, lo reemplazamos con el nuevo
        .flatMap(existingIngredient -> repo.save(ingredient))
        //Si no existe, lanzamos 404
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "El ingrediente con el ID especificado no existe.")))
        //3. Retornamos el ingrediente actualizado con un código de estado 200 OK.
        .map(updatedIngredient -> ResponseEntity.ok(updatedIngredient));

  }

  //Ejercicio 3: Construir Location sin localhost ni rutas rotas
  @PostMapping
  public Mono<ResponseEntity<Ingredient>> postIngredient(@RequestBody Ingredient ingredient) {
    //Primero, retornamos el resultado de la operación de guardado del 
    // ingrediente en el repositorio, que es un Mono<Ingredient>. 
    // Esto permite que la operación de guardado se realice de 
    // manera reactiva y se pueda suscribir a ella.
    return repo.save(ingredient)
        .map(i -> {
          // Usamos UriComponentsBuilder.fromPath para construir la ruta relativa de forma limpia
          URI location = UriComponentsBuilder
              .fromPath("/api/ingredients/{id}")
              .buildAndExpand(i.getId())
              .toUri();

          //Retornamos un ResponseEntity con el código de estado 201 Created y la 
          //cabecera Location apuntando a la ruta del nuevo recurso creado.
          return ResponseEntity.created(location).body(i);
        });
  }

  //Ejercicio 2: Eliminar de verdad y responder con semántica HTTP
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT) //Indicamos que la respuesta HTTP debe tener el código de estado 204 No Content, que es el código adecuado para indicar que la operación de eliminación se ha completado correctamente y que no hay contenido adicional en la respuesta.
  //Como en el ejercicio 1, cambiamos el retorno de void a Mono<Void> para mantener el publisher y permitir la suscripción a la operación de eliminación.
  public Mono<Void> deleteIngredient(@PathVariable String id) {
    //En lugar de simplemente eliminar el ingrediente y no retornar nada, retornamos el resultado de la operación de eliminación del repositorio, que es un Mono<Void>. Esto permite que la operación de eliminación se realice de manera reactiva y se pueda suscribir a ella.
    //Además, así mandamos la respuesta HTTP adecuada al cliente, indicando que la operación de eliminación se ha completado correctamente.
    return repo.deleteById(id);
  }

}
