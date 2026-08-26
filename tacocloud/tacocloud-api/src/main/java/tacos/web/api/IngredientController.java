package tacos.web.api;

import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

  @GetMapping
  public Flux<Ingredient> allIngredients() {
    return repo.findAll();
  }

  @GetMapping("/{id}")
  public Mono<Ingredient> byId(@PathVariable String id) {
    return repo.findById(id);
  }

  //Ejercicio 1: Actualizar un ingrediente sin perder el publisher
  @PutMapping("/{id}")
  //En lugar de retornar void, retornamos un Mono<Ingredient> para mantener el publisher y permitir la suscripción a la operación de actualización.
  public Mono<Ingredient> updateIngredient(@PathVariable String id, @RequestBody Ingredient ingredient) {
    //Comparamos el id del ingrediente recibido en la solicitud con el id de la ruta para asegurarnos de que coincidan antes de actualizar.
    if(!ingredient.getId().equals(id)) {
      //En lugar de retornar un error normal, retornamos un Mono.error con una excepción que indica que los IDs no coinciden. Esto permite que el flujo de datos continúe y se maneje adecuadamente en la suscripción.
      return Mono.error(new IllegalStateException("Given ingredient ID does not match the path variable ID"));
    }
    //Si los ids coinciden, retornamos el resultado de la operación de guardado del ingrediente en el repositorio, que es un Mono<Ingredient>. Esto permite que la operación de actualización se realice de manera reactiva y se pueda suscribir a ella.
    return repo.save(ingredient);
  }

  @PostMapping
  public Mono<ResponseEntity<Ingredient>> postIngredient(@RequestBody Mono<Ingredient> ingredient) {
    return ingredient
        .flatMap(repo::save)
        .map(i -> {
          HttpHeaders headers = new HttpHeaders();
          headers.setLocation(URI.create("http://localhost:8080/ingredients/" + i.getId()));
          return new ResponseEntity<Ingredient>(i, headers, HttpStatus.CREATED);
        });
  }

  @DeleteMapping("/{id}")
  public void deleteIngredient(@PathVariable String id) {
    repo.deleteById(id);
  }

}
