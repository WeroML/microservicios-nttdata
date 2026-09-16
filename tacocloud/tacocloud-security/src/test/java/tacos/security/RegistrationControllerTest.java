package tacos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import reactor.core.publisher.Mono;
import tacos.User;
import tacos.data.UserRepository;

public class RegistrationControllerTest {

  @Test
  public void processRegistration_shouldEncodePasswordWithBCryptAndPersistUserReactively() {
    // 1. ARRANGE
    UserRepository userRepo = mock(UserRepository.class);
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    when(userRepo.save(any(User.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

    RegistrationController controller = new RegistrationController(userRepo, passwordEncoder);

    RegistrationForm form = new RegistrationForm();
    form.setUsername("gustavo");
    form.setPassword("SuperSecretPassword123!");
    form.setFullname("Gustavo");
    form.setStreet("Avenida Siempre Viva");
    form.setCity("Guadalajara");
    form.setState("Jalisco");
    form.setZip("44100");
    form.setPhone("3312345678");
    form.setEmail("gustavo@example.com");

    // 2. ACT
    Mono<String> resultMono = controller.processRegistration(form);
    String viewResult = resultMono.block();

    // 3. ASSERT
    // Verifica que el resultado reactivo resuelva en la redirección esperada
    assertThat(viewResult).isEqualTo("redirect:/login");

    // Capturamos el usuario enviado al repositorio
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepo).save(userCaptor.capture());

    User savedUser = userCaptor.getValue();
    assertThat(savedUser.getUsername()).isEqualTo("gustavo");
    assertThat(savedUser.getEmail()).isEqualTo("gustavo@example.com");

    // 🎯 VERIFICACIÓN DE CONTRASEÑA PROTEGIDA:
    // 1. La contraseña NO debe ser texto plano
    assertThat(savedUser.getPassword()).isNotEqualTo("SuperSecretPassword123!");
    // 2. Debe ser un hash BCrypt válido (inicia con $2a$ o $2b$)
    assertThat(savedUser.getPassword()).matches("^\\$2[ab]\\$.*");
    // 3. El encoder debe verificar que el hash coincide con la contraseña original
    assertThat(passwordEncoder.matches("SuperSecretPassword123!", savedUser.getPassword())).isTrue();
  }

  @Test
  public void securityConfig_encoderBean_shouldBeBCryptPasswordEncoder() {
    SecurityConfig securityConfig = new SecurityConfig();
    PasswordEncoder encoder = securityConfig.encoder();

    assertThat(encoder).isNotNull();
    assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
  }
}
