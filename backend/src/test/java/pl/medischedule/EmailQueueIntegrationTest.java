package pl.medischedule;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.mail.javamail.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import pl.medischedule.repository.AppointmentRepository;
import pl.medischedule.service.*;

@SpringBootTest(
    properties = {
      "app.demo.enabled=true",
      "app.demo.password=Admin123!",
      "app.mail.enabled=true",
      "app.mail.dispatcher-enabled=false",
      "app.mail.max-attempts=3",
      "app.mail.retry-delay-seconds=1"
    })
@Import(EmailQueueIntegrationTest.ProbeConfig.class)
class EmailQueueIntegrationTest extends ApiIntegrationTestSupport {
  static final GenericContainer<?> SMTP = smtp();

  static GenericContainer<?> smtp() {
    var container =
        new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.34.0"))
            .withExposedPorts(1025, 8025);
    container.start();
    return container;
  }

  @DynamicPropertySource
  static void smtpProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.mail.host", SMTP::getHost);
    registry.add("spring.mail.port", () -> SMTP.getMappedPort(1025));
  }

  @Autowired EmailQueueService queue;
  @Autowired EmailDispatcher dispatcher;
  @Autowired JavaMailSender sender;
  @Autowired AppointmentRepository appointments;
  @Autowired PlatformTransactionManager transactions;
  @Autowired Probe probe;

  @Autowired
  @Qualifier("mailExecutor")
  Executor executor;

  @BeforeEach
  void resetMailQueue() throws Exception {
    ((ThreadPoolTaskExecutor) executor).submit(() -> {}).get(5, TimeUnit.SECONDS);
    probe.clear();
    jdbc.update("DELETE FROM email_jobs");
  }

  @AfterAll
  static void closeSmtp() {
    SMTP.stop();
  }

  @Test
  void bookingCreatesTwoDurableJobsAndDispatchesEventAfterCommitOnWorkerThread() throws Exception {
    long id = book(patient, "09:00");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM email_jobs WHERE id_appointment=?", Integer.class, id))
        .isEqualTo(2);
    assertThat(jdbc.queryForList("SELECT status FROM email_jobs", String.class))
        .containsOnly("pending");
    assertThat(probe.next(5000)).startsWith("mail-");
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM email_jobs WHERE attempts<>0", Integer.class))
        .isZero();
  }

  @Test
  void rolledBackTransactionDoesNotLeaveJobsOrPublishAfterCommitEvent() throws Exception {
    long id = book(patient, "09:00");
    ((ThreadPoolTaskExecutor) executor).submit(() -> {}).get(5, TimeUnit.SECONDS);
    probe.clear();
    jdbc.update("DELETE FROM email_jobs");
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              queue.confirmation(appointments.findById(id).orElseThrow());
              status.setRollbackOnly();
            });
    assertThat(jdbc.queryForObject("SELECT count(*) FROM email_jobs", Integer.class)).isZero();
    assertThat(probe.next(200)).isNull();
  }

  @Test
  void actualSmtpDeliveryMarksBothJobsAsSent() throws Exception {
    int before = mailbox().get("total").asInt();
    book(patient, "09:00");
    dispatcher.deliver();
    assertThat(jdbc.queryForList("SELECT status FROM email_jobs", String.class))
        .containsOnly("sent");
    assertThat(jdbc.queryForList("SELECT attempts FROM email_jobs", Integer.class)).containsOnly(1);
    assertThat(mailbox().get("total").asInt()).isEqualTo(before + 2);
  }

  @Test
  void smtpFailureKeepsReservationAndRetriesAfterRecovery() throws Exception {
    long id = book(patient, "09:00");
    withBrokenSmtp(dispatcher::deliver);
    assertThat(jdbc.queryForObject("SELECT status FROM appointments WHERE id=?", String.class, id))
        .isEqualTo("confirmed");
    assertThat(jdbc.queryForList("SELECT status FROM email_jobs", String.class))
        .containsOnly("pending");
    assertThat(jdbc.queryForList("SELECT attempts FROM email_jobs", Integer.class)).containsOnly(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM email_jobs WHERE last_error IS NOT NULL", Integer.class))
        .isEqualTo(2);
    makeDue();
    dispatcher.deliver();
    assertThat(jdbc.queryForList("SELECT status FROM email_jobs", String.class))
        .containsOnly("sent");
    assertThat(jdbc.queryForList("SELECT attempts FROM email_jobs", Integer.class)).containsOnly(2);
  }

  @Test
  void smtpFailureDoesNotMakeBackendUnhealthy() {
    withBrokenSmtp(
        () ->
            assertThatCode(
                    () ->
                        mvc.perform(get("/actuator/health"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.status").value("UP")))
                .doesNotThrowAnyException());
  }

  @Test
  void retryLimitAndAdministratorRetryRespectRoleAndCsrf() throws Exception {
    book(patient, "09:00");
    withBrokenSmtp(
        () -> {
          for (int attempt = 0; attempt < 3; attempt++) {
            makeDue();
            dispatcher.deliver();
          }
        });
    assertThat(jdbc.queryForList("SELECT status FROM email_jobs", String.class))
        .containsOnly("failed");
    long id = jdbc.queryForObject("SELECT min(id) FROM email_jobs", Long.class);
    mvc.perform(get("/api/admin/emails/{id}", id).session(patient))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/emails/{id}", id).session(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body").isString());
    mvc.perform(post("/api/admin/emails/{id}/retry", id).session(patient).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/admin/emails/{id}/retry", id).session(admin))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/admin/emails/{id}/retry", id).session(admin).with(csrf()))
        .andExpect(status().isNoContent());
    dispatcher.deliver();
    assertThat(jdbc.queryForObject("SELECT status FROM email_jobs WHERE id=?", String.class, id))
        .isEqualTo("sent");
    assertThat(jdbc.queryForObject("SELECT attempts FROM email_jobs WHERE id=?", Integer.class, id))
        .isEqualTo(1);
    mvc.perform(post("/api/admin/emails/{id}/retry", id).session(admin).with(csrf()))
        .andExpect(status().isConflict());
  }

  @Test
  void competingWorkersCannotClaimTheSameJob() throws Exception {
    book(patient, "09:00");
    assertThat(race(() -> queue.claim().size(), () -> queue.claim().size()))
        .containsExactlyInAnyOrder(2, 0);
  }

  @Test
  void expiredLeaseCanBeReclaimedAndOldAcknowledgementIsIgnored() throws Exception {
    book(patient, "09:00");
    var old = queue.claim();
    jdbc.update("UPDATE email_jobs SET lease_until=now()-interval '1 second'");
    var reclaimed = queue.claim();
    old.forEach(queue::sent);
    assertThat(jdbc.queryForList("SELECT status FROM email_jobs", String.class))
        .containsOnly("processing");
    assertThat(reclaimed).allMatch(job -> job.attempt() == 2);
    reclaimed.forEach(queue::sent);
    assertThat(jdbc.queryForList("SELECT status FROM email_jobs", String.class))
        .containsOnly("sent");
  }

  @Test
  void pagesAreStableFilteredAndRestrictedToAdministrators() throws Exception {
    long userId =
        jdbc.queryForObject("SELECT id_user FROM patients WHERE id=?", Long.class, patientId);
    for (int index = 0; index < 12; index++) {
      jdbc.update(
          "INSERT INTO email_jobs(id_user,recipient,subject,body,created_at) VALUES (?,?,?,?,?)",
          userId,
          "page-" + index + "@example.test",
          "Potwierdzenie",
          "Treść",
          java.sql.Timestamp.valueOf("2026-11-01 10:00:00"));
    }
    var first =
        body(
            mvc.perform(get("/api/admin/emails?page=0&size=5").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andReturn());
    var second =
        body(
            mvc.perform(get("/api/admin/emails?page=1&size=5").session(admin))
                .andExpect(status().isOk())
                .andReturn());
    Set<Long> ids = new HashSet<>();
    first.get("content").forEach(item -> ids.add(item.get("id").asLong()));
    second.get("content").forEach(item -> assertThat(ids.add(item.get("id").asLong())).isTrue());
    assertThat(first.get("content").size()).isEqualTo(5);
    assertThat(second.get("content").size()).isEqualTo(5);
    mvc.perform(get("/api/admin/emails?q=PAGE-11&status=pending").session(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get("/api/admin/emails?page=-1").session(admin)).andExpect(status().isBadRequest());
    mvc.perform(get("/api/admin/emails?size=101").session(admin))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/admin/emails?status=unknown").session(admin))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/admin/emails").session(patient)).andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/emails").session(doctor)).andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/emails")).andExpect(status().isUnauthorized());
  }

  private void makeDue() {
    jdbc.update(
        "UPDATE email_jobs SET next_attempt_at=now()-interval '1 second' WHERE status='pending'");
  }

  private void withBrokenSmtp(Runnable action) {
    JavaMailSenderImpl smtp = (JavaMailSenderImpl) sender;
    String host = smtp.getHost();
    int port = smtp.getPort();
    try {
      smtp.setHost("127.0.0.1");
      smtp.setPort(1);
      action.run();
    } finally {
      smtp.setHost(host);
      smtp.setPort(port);
    }
  }

  private JsonNode mailbox() throws Exception {
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://"
                        + SMTP.getHost()
                        + ":"
                        + SMTP.getMappedPort(8025)
                        + "/api/v1/messages"))
            .timeout(Duration.ofSeconds(10))
            .build();
    var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return json.readTree(response.body());
  }

  @TestConfiguration
  static class ProbeConfig {
    @Bean
    Probe probe() {
      return new Probe();
    }
  }

  public static class Probe {
    private final BlockingQueue<String> threads = new LinkedBlockingQueue<>();

    public void clear() {
      threads.clear();
    }

    public String next(long timeout) throws InterruptedException {
      return threads.poll(timeout, TimeUnit.MILLISECONDS);
    }

    @Async("mailExecutor")
    @TransactionalEventListener
    public void received(EmailQueueService.Queued event) {
      threads.add(Thread.currentThread().getName());
    }
  }
}
