package pl.medischedule.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.medischedule.domain.*;
import pl.medischedule.repository.*;
import pl.medischedule.security.CurrentUser;
import pl.medischedule.web.ApiException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
  @Mock UserRepository users;
  @Mock RoleRepository roles;
  @Mock PatientRepository patients;
  @Mock DoctorRepository doctors;
  @Mock SpecializationRepository specializations;
  @Mock CurrentUser current;
  @Mock LoginProtectionService loginProtection;
  PasswordEncoder passwords = new BCryptPasswordEncoder(4);
  UserService service;

  @BeforeEach
  void setup() {
    service =
        new UserService(
            users, roles, patients, doctors, specializations, passwords, current, loginProtection);
  }

  @Test
  void publicRegistrationCannotChooseAdministratorRole() throws Exception {
    var body =
        new ObjectMapper()
            .readValue(
                """
                {"email":"NEW@EXAMPLE.COM","password":"Patient123!","name":"Nowy Pacjent","pesel":"12345678901","role":"admin"}
                """,
                UserService.RegisterRequest.class);
    Role patientRole = role("patient");
    when(roles.findByName("patient")).thenReturn(Optional.of(patientRole));
    when(users.saveAndFlush(any(User.class)))
        .thenAnswer(
            invocation -> {
              User user = invocation.getArgument(0);
              user.id = 3L;
              return user;
            });
    var result = service.register(body);
    assertEquals("patient", result.role());
    assertEquals("new@example.com", result.email());
    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).saveAndFlush(saved.capture());
    assertTrue(passwords.matches("Patient123!", saved.getValue().password));
    assertNotEquals("Patient123!", saved.getValue().password);
    verify(roles, never()).findByName("admin");
  }

  @Test
  void administratorCannotDeleteOwnAccount() {
    User actor = user(1L, "admin");
    when(current.require("admin")).thenReturn(actor);
    when(roles.lockByName("admin")).thenReturn(Optional.of(actor.role));
    when(users.findById(1L)).thenReturn(Optional.of(actor));
    assertThrows(ApiException.class, () -> service.delete(1L));
    verify(users, never()).delete(any());
  }

  @Test
  void lastActiveAdministratorCannotBeBlocked() {
    User actor = user(1L, "admin");
    User target = user(2L, "admin");
    when(current.require("admin")).thenReturn(actor);
    when(roles.lockByName("admin")).thenReturn(Optional.of(actor.role));
    when(users.findById(2L)).thenReturn(Optional.of(target));
    when(users.countByRoleNameAndBlockedFalse("admin")).thenReturn(1L);
    assertThrows(ApiException.class, () -> service.block(2L, true));
    assertFalse(target.blocked);
  }

  @Test
  void rejectsWeakPasswordAndBcryptTruncation() {
    assertThrows(ApiException.class, () -> UserService.validatePassword("password"));
    assertThrows(ApiException.class, () -> UserService.validatePassword("Aa1!" + "ą".repeat(40)));
    assertDoesNotThrow(() -> UserService.validatePassword("Patient123!"));
  }

  @Test
  void bcryptAcceptsPhpTwoYHashes() {
    String phpStyle = passwords.encode("Patient123!").replaceFirst("\\$2a\\$", "\\$2y\\$");
    assertTrue(passwords.matches("Patient123!", phpStyle));
  }

  @Test
  void unblockingAlsoResetsLoginLocksAndAlerts() {
    User actor = user(1L, "admin");
    User target = user(2L, "patient");
    target.blocked = true;
    when(current.require("admin")).thenReturn(actor);
    when(roles.lockByName("admin")).thenReturn(Optional.of(actor.role));
    when(users.findById(2L)).thenReturn(Optional.of(target));
    when(users.save(target)).thenReturn(target);
    service.block(2L, false);
    verify(loginProtection).resetForUser(target.email);
    assertFalse(target.blocked);
  }

  private static Role role(String name) {
    Role role = new Role();
    role.name = name;
    role.id = 1L;
    return role;
  }

  private static User user(Long id, String role) {
    User user = new User();
    user.id = id;
    user.role = role(role);
    user.email = "user" + id + "@example.com";
    return user;
  }
}
