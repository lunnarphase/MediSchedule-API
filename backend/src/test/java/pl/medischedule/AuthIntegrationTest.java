package pl.medischedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = "app.demo.enabled=false")
@AutoConfigureMockMvc
class AuthIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  static final String PASSWORD = "Account123!";
  static final AtomicInteger SERIAL = new AtomicInteger();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder passwords;
  String adminEmail;
  String doctorEmail;
  String patientEmail;
  long adminId;
  long doctorUserId;
  long patientId;
  String ipAddress;

  @BeforeEach
  void fixtures() {
    ipAddress = "192.0.2." + SERIAL.incrementAndGet();
    String suffix = UUID.randomUUID().toString();
    adminEmail = "admin-" + suffix + "@example.test";
    doctorEmail = "doctor-" + suffix + "@example.test";
    patientEmail = "patient-" + suffix + "@example.test";
    adminId = user(adminEmail, "admin");
    doctorUserId = user(doctorEmail, "doctor");
    patientId = user(patientEmail, "patient");
    jdbc.update("INSERT INTO doctors(id_user) VALUES (?)", doctorUserId);
    jdbc.update("INSERT INTO patients(id_user,pesel) VALUES (?,?)", patientId, pesel());
  }

  @Test
  void publicRegistrationIgnoresRequestedRoleAndStoresHashedPassword() throws Exception {
    String email = "new-" + UUID.randomUUID() + "@example.test";
    mvc.perform(
            post("/api/auth/register")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "email",
                            email.toUpperCase(),
                            "password",
                            PASSWORD,
                            "name",
                            "Nowy Pacjent",
                            "pesel",
                            pesel(),
                            "role",
                            "admin"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.role").value("patient"))
        .andExpect(jsonPath("$.patientId").isNumber())
        .andExpect(jsonPath("$.password").doesNotExist());

    String stored =
        jdbc.queryForObject("SELECT password FROM users WHERE email=?", String.class, email);
    assertThat(stored).isNotEqualTo(PASSWORD);
    assertThat(passwords.matches(PASSWORD, stored)).isTrue();
    assertThat(
            body(mvc.perform(get("/api/auth/me").session(login(email, PASSWORD))).andReturn())
                .get("role")
                .asText())
        .isEqualTo("patient");
  }

  @Test
  void registrationRejectsDuplicateEmailPeselAndWeakPassword() throws Exception {
    String email = "duplicate-" + UUID.randomUUID() + "@example.test";
    String pesel = pesel();
    register(email, PASSWORD, pesel, 201);
    register(email, PASSWORD, pesel(), 409);
    register("other-" + email, PASSWORD, pesel, 409);
    register("weak-" + email, "password", pesel(), 400);
  }

  @Test
  void allRolesCanLogInButProtectedRequestsRequireSessionAndCsrf() throws Exception {
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());

    for (var account :
        Map.of(adminEmail, "admin", doctorEmail, "doctor", patientEmail, "patient").entrySet()) {
      var session = login(account.getKey(), PASSWORD);
      mvc.perform(get("/api/auth/me").session(session))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.role").value(account.getValue()))
          .andExpect(jsonPath("$.password").doesNotExist());
    }
    var patient = login(patientEmail, PASSWORD);
    mvc.perform(get("/api/admin/users").session(patient)).andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/security").session(patient)).andExpect(status().isForbidden());
    mvc.perform(put("/api/profile").session(patient).contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/auth/csrf").session(patient))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty());
  }

  @Test
  void logoutInvalidatesSession() throws Exception {
    var patient = login(patientEmail, PASSWORD);
    mvc.perform(post("/api/auth/logout").session(patient).with(csrf()))
        .andExpect(status().isNoContent());
    assertThat(patient.isInvalid()).isTrue();
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void passwordChangeValidatesCurrentPasswordAndReplacesStoredHash() throws Exception {
    var patient = login(patientEmail, PASSWORD);
    String replacement = "Changed123!";
    mvc.perform(
            put("/api/profile/password")
                .session(patient)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("currentPassword", "Wrong123!", "newPassword", replacement))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            put("/api/profile/password")
                .session(patient)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("currentPassword", PASSWORD, "newPassword", replacement))))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/auth/login")
                .with(this::remoteAddress)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(Map.of("email", patientEmail, "password", PASSWORD))))
        .andExpect(status().isUnauthorized());
    login(patientEmail, replacement);
  }

  @Test
  void blockingAccountInvalidatesExistingSessionAndUnblockingAllowsLogin() throws Exception {
    var patient = login(patientEmail, PASSWORD);
    var admin = login(adminEmail, PASSWORD);
    mvc.perform(
            patch("/api/admin/users/{id}/block", patientId)
                .session(admin)
                .with(csrf())
                .contentType("application/json")
                .content("{\"blocked\":true}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/profile").session(patient)).andExpect(status().isUnauthorized());
    assertThat(patient.isInvalid()).isTrue();
    mvc.perform(
            patch("/api/admin/users/{id}/block", patientId)
                .session(admin)
                .with(csrf())
                .contentType("application/json")
                .content("{\"blocked\":false}"))
        .andExpect(status().isOk());
    login(patientEmail, PASSWORD);
  }

  @Test
  void roleChangeAppliesToAnExistingSession() throws Exception {
    var admin = login(adminEmail, PASSWORD);
    var doctor = login(doctorEmail, PASSWORD);
    mvc.perform(
            put("/api/admin/users/{id}", doctorUserId)
                .session(admin)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "email",
                            doctorEmail,
                            "name",
                            "Nowa rola",
                            "role",
                            "patient",
                            "pesel",
                            pesel()))))
        .andExpect(status().isOk());
    mvc.perform(get("/api/auth/me").session(doctor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("patient"));
    mvc.perform(get("/api/admin/users").session(doctor)).andExpect(status().isForbidden());
  }

  @Test
  void repeatedInvalidLoginIsThrottledWithoutLockingAnotherAccount() throws Exception {
    for (int attempt = 0; attempt < 3; attempt++) {
      mvc.perform(
              post("/api/auth/login")
                  .with(this::remoteAddress)
                  .with(csrf())
                  .contentType("application/json")
                  .content(
                      json.writeValueAsString(
                          Map.of("email", patientEmail, "password", "Wrong123!"))))
          .andExpect(status().isUnauthorized());
    }
    mvc.perform(
            post("/api/auth/login")
                .with(this::remoteAddress)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(Map.of("email", patientEmail, "password", PASSWORD))))
        .andExpect(status().isTooManyRequests());
    login(doctorEmail, PASSWORD);
  }

  @Test
  void administratorCanManageUsersButCannotDeleteBlockOrDemoteItself() throws Exception {
    var admin = login(adminEmail, PASSWORD);
    mvc.perform(delete("/api/admin/users/{id}", adminId).session(admin).with(csrf()))
        .andExpect(status().isConflict());
    mvc.perform(
            patch("/api/admin/users/{id}/block", adminId)
                .session(admin)
                .with(csrf())
                .contentType("application/json")
                .content("{\"blocked\":true}"))
        .andExpect(status().isConflict());
    mvc.perform(
            put("/api/admin/users/{id}", adminId)
                .session(admin)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "email",
                            adminEmail,
                            "name",
                            "Administrator",
                            "role",
                            "patient",
                            "pesel",
                            pesel()))))
        .andExpect(status().isConflict());
    JsonNode created =
        body(
            mvc.perform(
                    post("/api/admin/users")
                        .session(admin)
                        .with(csrf())
                        .contentType("application/json")
                        .content(
                            json.writeValueAsString(
                                Map.of(
                                    "email",
                                    "added-" + UUID.randomUUID() + "@example.test",
                                    "password",
                                    PASSWORD,
                                    "name",
                                    "Dodany Lekarz",
                                    "role",
                                    "doctor"))))
                .andExpect(status().isCreated())
                .andReturn());
    assertThat(created.get("doctorId").isNumber()).isTrue();
    mvc.perform(
            delete("/api/admin/users/{id}", created.get("id").asLong()).session(admin).with(csrf()))
        .andExpect(status().isNoContent());
  }

  private long user(String email, String role) {
    return jdbc.queryForObject(
        "INSERT INTO users(email,password,username,id_role) VALUES (?,?,?,"
            + "(SELECT id FROM roles WHERE name=?)) RETURNING id",
        Long.class,
        email,
        passwords.encode(PASSWORD),
        "Konto testowe",
        role);
  }

  private void register(String email, String password, String pesel, int expected)
      throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "email",
                            email,
                            "password",
                            password,
                            "name",
                            "Nowy Pacjent",
                            "pesel",
                            pesel))))
        .andExpect(status().is(expected));
  }

  private MockHttpSession login(String email, String password) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/auth/login")
                    .with(this::remoteAddress)
                    .session(new MockHttpSession())
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("email", email, "password", password))))
            .andExpect(status().isOk())
            .andReturn();
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private JsonNode body(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsByteArray());
  }

  private MockHttpServletRequest remoteAddress(MockHttpServletRequest request) {
    request.setRemoteAddr(ipAddress);
    return request;
  }

  private static String pesel() {
    return String.format("%011d", SERIAL.incrementAndGet());
  }
}
