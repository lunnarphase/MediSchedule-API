package pl.medischedule.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.medischedule.domain.*;
import pl.medischedule.repository.*;
import pl.medischedule.security.CurrentUser;
import pl.medischedule.web.ApiException;

@Service
@Transactional
public class UserService {
  private final UserRepository users;
  private final RoleRepository roles;
  private final PatientRepository patients;
  private final DoctorRepository doctors;
  private final SpecializationRepository specializations;
  private final PasswordEncoder passwords;
  private final CurrentUser current;
  private final LoginProtectionService loginProtection;

  public UserService(
      UserRepository users,
      RoleRepository roles,
      PatientRepository patients,
      DoctorRepository doctors,
      SpecializationRepository specializations,
      PasswordEncoder passwords,
      CurrentUser current,
      LoginProtectionService loginProtection) {
    this.users = users;
    this.roles = roles;
    this.patients = patients;
    this.doctors = doctors;
    this.specializations = specializations;
    this.passwords = passwords;
    this.current = current;
    this.loginProtection = loginProtection;
  }

  public record UserView(
      Long id,
      String email,
      String name,
      String role,
      boolean blocked,
      String pesel,
      String phone,
      Long patientId,
      Long doctorId) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record RegisterRequest(
      @NotBlank @Email @Size(max = 255) String email,
      @NotBlank @Size(min = 8, max = 72) String password,
      @NotBlank @Size(min = 3, max = 100) String name,
      @NotBlank @Pattern(regexp = "[0-9]{11}") String pesel,
      @Size(max = 20) @Pattern(regexp = "[+0-9 ()-]*") String phone) {}

  public record ProfileRequest(
      @NotBlank @Email @Size(max = 255) String email,
      @NotBlank @Size(min = 3, max = 100) String name,
      @Size(max = 20) @Pattern(regexp = "[+0-9 ()-]*") String phone) {}

  public record PasswordRequest(
      @NotBlank @Size(max = 1024) String currentPassword,
      @NotBlank @Size(min = 8, max = 72) String newPassword) {}

  public record AddUserRequest(
      @NotBlank @Email @Size(max = 255) String email,
      @NotBlank @Size(min = 8, max = 72) String password,
      @NotBlank @Size(min = 3, max = 100) String name,
      @NotBlank @Pattern(regexp = "patient|doctor|admin") String role,
      @Pattern(regexp = "[0-9]{11}") String pesel,
      @Size(max = 20) @Pattern(regexp = "[+0-9 ()-]*") String phone,
      Long specializationId) {}

  public record EditUserRequest(
      @NotBlank @Email @Size(max = 255) String email,
      @NotBlank @Size(min = 3, max = 100) String name,
      @Pattern(regexp = "patient|doctor|admin") String role,
      @Pattern(regexp = "[0-9]{11}") String pesel,
      @Size(max = 20) @Pattern(regexp = "[+0-9 ()-]*") String phone,
      Long specializationId) {}

  @Transactional(readOnly = true)
  public UserView view(User user) {
    Patient patient = patients.findByUserId(user.id).orElse(null);
    Doctor doctor = doctors.findByUserId(user.id).orElse(null);
    return new UserView(
        user.id,
        user.email,
        user.name,
        user.role.name,
        user.blocked,
        patient == null ? null : patient.pesel,
        patient == null ? null : patient.phone,
        patient == null ? null : patient.id,
        doctor == null ? null : doctor.id);
  }

  public UserView register(RegisterRequest request) {
    User user =
        create(
            request.email(),
            request.password(),
            request.name(),
            "patient",
            request.pesel(),
            request.phone(),
            null);
    return view(user);
  }

  @Transactional(readOnly = true)
  public UserView me() {
    return view(current.user());
  }

  public UserView updateProfile(ProfileRequest request) {
    User user = current.user();
    ensureEmailAvailable(request.email(), user.id);
    user.email = normalizeEmail(request.email());
    user.name = cleanName(request.name());
    users.save(user);
    patients
        .findByUserId(user.id)
        .ifPresent(
            patient -> {
              patient.phone = request.phone();
              patients.save(patient);
            });
    return view(user);
  }

  public void changePassword(PasswordRequest request) {
    User user = current.user();
    if (!passwords.matches(request.currentPassword(), user.password))
      throw new ApiException(400, "Obecne hasło jest nieprawidłowe.");
    validatePassword(request.newPassword());
    user.password = passwords.encode(request.newPassword());
    users.save(user);
  }

  @Transactional(readOnly = true)
  public List<UserView> list() {
    current.require("admin");
    return users.findAllByOrderByIdAsc().stream().map(this::view).toList();
  }

  public UserView add(AddUserRequest request) {
    current.require("admin");
    return view(
        create(
            request.email(),
            request.password(),
            request.name(),
            request.role(),
            request.pesel(),
            request.phone(),
            request.specializationId()));
  }

  public UserView edit(Long id, EditUserRequest request) {
    User actor = current.require("admin");
    lockAdmins();
    User user = find(id);
    String roleName = request.role() == null ? user.role.name : request.role();
    if (user.role.name.equals("admin") && !roleName.equals("admin")) protectAdmin(actor, user);
    ensureEmailAvailable(request.email(), user.id);
    user.email = normalizeEmail(request.email());
    user.name = cleanName(request.name());
    user.role = role(roleName);
    users.save(user);
    ensureProfile(user, request.pesel(), request.phone(), request.specializationId());
    patients
        .findByUserId(user.id)
        .ifPresent(
            patient -> {
              if (request.phone() != null) patient.phone = request.phone();
              patients.save(patient);
            });
    return view(user);
  }

  public UserView block(Long id, boolean blocked) {
    User actor = current.require("admin");
    lockAdmins();
    User user = find(id);
    if (blocked) {
      if (actor.id.equals(user.id))
        throw new ApiException(409, "Nie możesz zablokować własnego konta.");
      if (user.role.name.equals("admin")) protectAdmin(actor, user);
    } else {
      loginProtection.resetForUser(user.email);
    }
    user.blocked = blocked;
    return view(users.save(user));
  }

  public void delete(Long id) {
    User actor = current.require("admin");
    lockAdmins();
    User user = find(id);
    if (actor.id.equals(user.id)) throw new ApiException(409, "Nie możesz usunąć własnego konta.");
    if (user.role.name.equals("admin")) protectAdmin(actor, user);
    users.delete(user);
    users.flush();
  }

  private User create(
      String email,
      String password,
      String name,
      String roleName,
      String pesel,
      String phone,
      Long specializationId) {
    ensureEmailAvailable(email, null);
    validatePassword(password);
    User user = new User();
    user.email = normalizeEmail(email);
    user.name = cleanName(name);
    user.password = passwords.encode(password);
    user.role = role(roleName);
    users.saveAndFlush(user);
    ensureProfile(user, pesel, phone, specializationId);
    return user;
  }

  private void ensureProfile(User user, String pesel, String phone, Long specializationId) {
    if (user.role.name.equals("patient") && patients.findByUserId(user.id).isEmpty()) {
      if (pesel == null || !pesel.matches("[0-9]{11}"))
        throw new ApiException(400, "Profil pacjenta wymaga 11-cyfrowego numeru PESEL.");
      if (patients.existsByPesel(pesel))
        throw new ApiException(409, "Nie można utworzyć profilu dla podanych danych.");
      Patient patient = new Patient();
      patient.user = user;
      patient.pesel = pesel;
      patient.phone = phone;
      patients.save(patient);
    }
    if (user.role.name.equals("doctor")) {
      Doctor doctor =
          doctors
              .findByUserId(user.id)
              .orElseGet(
                  () -> {
                    Doctor created = new Doctor();
                    created.user = user;
                    created.bio = "";
                    created.visitPrice = BigDecimal.ZERO;
                    created.visitDuration = 30;
                    return created;
                  });
      if (specializationId != null) {
        Specialization specialization =
            specializations
                .findById(specializationId)
                .orElseThrow(() -> new ApiException(400, "Nieznana specjalizacja."));
        doctor.specializations.add(specialization);
      }
      doctors.save(doctor);
    }
  }

  private Role role(String name) {
    if (!Set.of("patient", "doctor", "admin").contains(name))
      throw new ApiException(400, "Nieznana rola.");
    return roles
        .findByName(name)
        .orElseThrow(() -> new ApiException(500, "Brak konfiguracji ról."));
  }

  private void lockAdmins() {
    roles.lockByName("admin").orElseThrow(() -> new ApiException(500, "Brak konfiguracji ról."));
  }

  private void protectAdmin(User actor, User user) {
    if (actor.id.equals(user.id))
      throw new ApiException(409, "Nie możesz odebrać sobie uprawnień administratora.");
    if (!user.blocked && users.countByRoleNameAndBlockedFalse("admin") <= 1)
      throw new ApiException(409, "Operacja pozostawiłaby aplikację bez aktywnego administratora.");
  }

  private User find(Long id) {
    return users
        .findById(id)
        .orElseThrow(() -> new ApiException(404, "Nie znaleziono użytkownika."));
  }

  private void ensureEmailAvailable(String email, Long excludedId) {
    users
        .findByEmailIgnoreCase(normalizeEmail(email))
        .ifPresent(
            existing -> {
              if (!existing.id.equals(excludedId))
                throw new ApiException(409, "Nie można zapisać konta dla podanych danych.");
            });
  }

  public static String normalizeEmail(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private static String cleanName(String value) {
    String name = value.trim();
    if (name.length() < 3 || name.length() > 100)
      throw new ApiException(400, "Nazwa musi mieć od 3 do 100 znaków.");
    return name;
  }

  public static void validatePassword(String value) {
    if (value == null
        || value.length() < 8
        || value.getBytes(StandardCharsets.UTF_8).length > 72
        || !value.matches("(?s).*\\p{Lu}.*")
        || !value.matches("(?s).*\\p{Ll}.*")
        || !value.matches("(?s).*\\d.*")
        || !value.matches("(?s).*[^\\p{L}\\p{N}\\s].*"))
      throw new ApiException(
          400, "Hasło musi mieć 8-72 bajty, wielką i małą literę, cyfrę oraz znak specjalny.");
  }
}
