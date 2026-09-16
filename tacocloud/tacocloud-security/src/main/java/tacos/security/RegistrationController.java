package tacos.security;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.publisher.Mono;
import tacos.data.UserRepository;

@Controller
@RequestMapping("/register")
public class RegistrationController {
  
  private UserRepository userRepo;
  private PasswordEncoder passwordEncoder;

  public RegistrationController(
      UserRepository userRepo, PasswordEncoder passwordEncoder) {
    this.userRepo = userRepo;
    this.passwordEncoder = passwordEncoder;
  }
  
  @GetMapping
  public String registerForm() {
    return "registration";
  }
  
  // Ejercicio 9: Registro reactivo con contraseñas protegidas
  @PostMapping
  public Mono<String> processRegistration(RegistrationForm form) {
    return userRepo.save(form.toUser(passwordEncoder))
        .map(user -> "redirect:/login");
  }

}
