package pl.medischedule.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import pl.medischedule.domain.LoginAttempt;

class LoginProtectionServiceTest {
  @Test
  void successResetsAccountWindowWithoutResettingOtherAccounts() {
    var before = attempt("a@example.com", "10.0.0.1", false, 1);
    var success = attempt("a@example.com", "10.0.0.1", true, 2);
    var after = attempt("a@example.com", "10.0.0.2", false, 3);
    var other = attempt("b@example.com", "10.0.0.2", false, 4);
    assertEquals(
        List.of(after),
        LoginProtectionService.accountFailures(
            List.of(before, success, after, other), "A@example.com"));
  }

  @Test
  void distributedLockNeedsFourFailuresAndTwoAddresses() {
    var first = attempt("a@example.com", "10.0.0.1", false, 1);
    var second = attempt("a@example.com", "10.0.0.2", false, 2);
    assertFalse(
        LoginProtectionService.distributedAccountAttack(List.of(first, first, first, first)));
    assertFalse(LoginProtectionService.distributedAccountAttack(List.of(first, second, second)));
    assertTrue(
        LoginProtectionService.distributedAccountAttack(List.of(first, first, second, second)));
  }

  private LoginAttempt attempt(String email, String ip, boolean success, long second) {
    var attempt = new LoginAttempt();
    attempt.email = email;
    attempt.ipAddress = ip;
    attempt.success = success;
    attempt.attemptedAt = Instant.ofEpochSecond(second);
    return attempt;
  }
}
