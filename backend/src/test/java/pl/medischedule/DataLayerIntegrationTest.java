package pl.medischedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = "app.demo.enabled=false")
@Transactional
class DataLayerIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  static final LocalDate DAY = LocalDate.of(2030, 1, 15);
  static final AtomicInteger SERIAL = new AtomicInteger();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired Flyway flyway;

  @Test
  void migrationCreatesSchemaAndDoesNotDuplicateDictionaries() {
    assertThat(flyway.migrate().migrationsExecuted).isZero();
    assertThat(flyway.info().applied()).hasSize(1);
    assertThat(jdbc.queryForList("SELECT name FROM roles", String.class))
        .containsExactlyInAnyOrder("admin", "doctor", "patient");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM specializations", Long.class))
        .isEqualTo(4L);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isZero();
  }

  @Test
  void databaseRejectsPartiallyOverlappingAppointmentsForDoctor() {
    long doctor = doctor();
    appointment(doctor, patient(), "09:00", "10:00", "confirmed");

    assertThatThrownBy(() -> appointment(doctor, patient(), "09:30", "10:30", "confirmed"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("no_overlapping_doctor_appointments");
  }

  @Test
  void databaseRejectsOverlappingAppointmentsForPatientWithDifferentDoctors() {
    long patient = patient();
    appointment(doctor(), patient, "09:00", "10:00", "confirmed");

    assertThatThrownBy(() -> appointment(doctor(), patient, "09:30", "10:30", "confirmed"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("no_overlapping_patient_appointments");
  }

  @Test
  void cancelledAppointmentsAndAdjacentSlotsDoNotBlockReservation() {
    long doctor = doctor();
    long patient = patient();
    appointment(doctor, patient, "09:00", "10:00", "cancelled");
    appointment(doctor, patient, "09:00", "10:00", "confirmed");
    appointment(doctor, patient, "10:00", "10:30", "confirmed");

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM appointments WHERE id_doctor=?", Long.class, doctor))
        .isEqualTo(3L);
  }

  private long user(String role) {
    return jdbc.queryForObject(
        "INSERT INTO users(email,password,username,id_role)"
            + " VALUES (?, 'test-password-hash', 'Konto testowe',"
            + " (SELECT id FROM roles WHERE name=?)) RETURNING id",
        Long.class,
        UUID.randomUUID() + "@example.test",
        role);
  }

  private long doctor() {
    return jdbc.queryForObject(
        "INSERT INTO doctors(id_user) VALUES (?) RETURNING id", Long.class, user("doctor"));
  }

  private long patient() {
    return jdbc.queryForObject(
        "INSERT INTO patients(id_user,pesel) VALUES (?,?) RETURNING id",
        Long.class,
        user("patient"),
        String.format("%011d", SERIAL.incrementAndGet()));
  }

  private void appointment(long doctor, long patient, String start, String end, String status) {
    LocalTime time = LocalTime.parse(start);
    jdbc.update(
        "INSERT INTO appointments(id_doctor,id_patient,appointment_date,appointment_time,"
            + "starts_at,ends_at,status) VALUES (?,?,?,?,?,?,?)",
        doctor,
        patient,
        DAY,
        time,
        DAY.atTime(time),
        DAY.atTime(LocalTime.parse(end)),
        status);
  }
}
