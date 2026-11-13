package pl.medischedule;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties = "app.demo.enabled=true")
@AutoConfigureMockMvc
abstract class ApiIntegrationTestSupport {
  static final PostgreSQLContainer<?> POSTGRES = database();
  static final AtomicInteger SERIAL = new AtomicInteger(1000);
  static final String PASSWORD = "Workflow123!";

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  private static PostgreSQLContainer<?> database() {
    var database = new PostgreSQLContainer<>("postgres:16");
    database.start();
    return database;
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  MockHttpSession admin;
  MockHttpSession doctor;
  MockHttpSession patient;
  MockHttpSession otherPatient;
  long doctorId;
  long patientId;
  long otherPatientId;
  LocalDate day;

  @BeforeEach
  void fixtures() throws Exception {
    int serial = SERIAL.incrementAndGet();
    String suffix = UUID.randomUUID().toString();
    day = LocalDate.now(ZoneId.of("Europe/Warsaw")).plusDays(30L + serial);
    admin = login("admin@medischedule.local", "Admin123!");
    doctor = createDoctor("doctor-" + suffix + "@example.test");
    doctorId =
        body(mvc.perform(get("/api/doctor/profile").session(doctor)).andReturn())
            .get("id")
            .asLong();
    patient = register("patient-" + suffix + "@example.test", pesel(serial * 2));
    otherPatient = register("other-" + suffix + "@example.test", pesel(serial * 2 + 1));
    patientId =
        body(mvc.perform(get("/api/profile").session(patient)).andReturn())
            .get("patientId")
            .asLong();
    otherPatientId =
        body(mvc.perform(get("/api/profile").session(otherPatient)).andReturn())
            .get("patientId")
            .asLong();
    replaceAvailability("09:00", "12:00").andExpect(status().isOk());
  }

  MockHttpSession createDoctor(String email) throws Exception {
    mvc.perform(
            post("/api/admin/users")
                .session(admin)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "email",
                            email,
                            "password",
                            PASSWORD,
                            "name",
                            "Lekarz Testowy",
                            "role",
                            "doctor"))))
        .andExpect(status().isCreated());
    var session = login(email, PASSWORD);
    mvc.perform(
            put("/api/doctor/profile")
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content("{\"bio\":\"Profil testowy\",\"visitPrice\":200,\"visitDuration\":45}"))
        .andExpect(status().isOk());
    return session;
  }

  ResultActions replaceAvailability(String start, String end) throws Exception {
    return mvc.perform(
        put("/api/doctor/availability")
            .session(doctor)
            .with(csrf())
            .contentType("application/json")
            .content(
                json.writeValueAsString(
                    Map.of(
                        "from",
                        day.toString(),
                        "to",
                        day.toString(),
                        "ranges",
                        java.util.List.of(
                            Map.of("date", day.toString(), "startTime", start, "endTime", end))))));
  }

  JsonNode body(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsByteArray());
  }

  private MockHttpSession register(String email, String pesel) throws Exception {
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
                            PASSWORD,
                            "name",
                            "Pacjent Testowy",
                            "pesel",
                            pesel))))
        .andExpect(status().isCreated());
    return login(email, PASSWORD);
  }

  MockHttpSession login(String email, String password) throws Exception {
    var result =
        mvc.perform(
                post("/api/auth/login")
                    .session(new MockHttpSession())
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("email", email, "password", password))))
            .andExpect(status().isOk())
            .andReturn();
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  static String pesel(int serial) {
    String ten = "900101" + String.format("%04d", serial);
    int[] weights = {1, 3, 7, 9, 1, 3, 7, 9, 1, 3};
    int sum = 0;
    for (int i = 0; i < 10; i++) sum += (ten.charAt(i) - '0') * weights[i];
    return ten + (10 - sum % 10) % 10;
  }

  long book(MockHttpSession session, String time) throws Exception {
    return body(requestBooking(session, doctorId, time).andExpect(status().isCreated()).andReturn())
        .get("id")
        .asLong();
  }

  ResultActions requestBooking(MockHttpSession session, long selectedDoctor, String time)
      throws Exception {
    return mvc.perform(
        post("/api/appointments")
            .session(session)
            .with(csrf())
            .contentType("application/json")
            .content(
                json.writeValueAsString(
                    Map.of("doctorId", selectedDoctor, "date", day.toString(), "time", time))));
  }

  void complete(long id) throws Exception {
    mvc.perform(
            patch("/api/appointments/{id}/status", id)
                .session(doctor)
                .with(csrf())
                .contentType("application/json")
                .content("{\"status\":\"completed\",\"recommendations\":\"Kontrola za miesiąc.\"}"))
        .andExpect(status().isOk());
  }

  List<Integer> race(Callable<Integer> first, Callable<Integer> second) throws Exception {
    var gate = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var a = pool.submit(() -> afterGate(gate, first));
      var b = pool.submit(() -> afterGate(gate, second));
      gate.countDown();
      return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }
  }

  private int afterGate(CountDownLatch gate, Callable<Integer> task) throws Exception {
    if (!gate.await(10, TimeUnit.SECONDS))
      throw new IllegalStateException("Przekroczono czas oczekiwania");
    return task.call();
  }
}
