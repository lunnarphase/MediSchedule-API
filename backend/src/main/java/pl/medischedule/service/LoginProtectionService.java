package pl.medischedule.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import pl.medischedule.domain.*;
import pl.medischedule.repository.*;
import pl.medischedule.web.ApiException;

@Service
public class LoginProtectionService {
  private final LoginAttemptRepository attempts;
  private final BlockedIpRepository blocks;
  private final NotificationService notifications;
  private final NotificationRepository notificationRecords;

  public LoginProtectionService(
      LoginAttemptRepository attempts,
      BlockedIpRepository blocks,
      NotificationService notifications,
      NotificationRepository notificationRecords) {
    this.attempts = attempts;
    this.blocks = blocks;
    this.notifications = notifications;
    this.notificationRecords = notificationRecords;
  }

  @Transactional(readOnly = true)
  public void check(String email, String ip) {
    Instant now = Instant.now();
    boolean ipBlocked =
        blocks
            .findById(ip)
            .map(block -> block.blockedUntil == null || block.blockedUntil.isAfter(now))
            .orElse(false);
    List<LoginAttempt> recent =
        attempts.findByAttemptedAtAfterOrderByAttemptedAtDesc(now.minusSeconds(900));
    List<LoginAttempt> account = accountFailures(recent, email);
    long pair = account.stream().filter(a -> ip.equals(a.ipAddress)).count();
    if (ipBlocked || pair >= 3 || distributedAccountAttack(account))
      throw new ApiException(429, "Zbyt wiele prób logowania. Spróbuj ponownie później.");
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(String email, String ip, boolean success) {
    Instant now = Instant.now();
    List<LoginAttempt> before =
        attempts.findByAttemptedAtAfterOrderByAttemptedAtDesc(now.minusSeconds(900));
    LoginAttempt attempt = new LoginAttempt();
    attempt.email = email;
    attempt.ipAddress = ip;
    attempt.success = success;
    attempt.attemptedAt = now;
    attempts.saveAndFlush(attempt);
    if (success) return;
    var after = new ArrayList<>(before);
    after.add(attempt);
    if (distributedAccountAttack(accountFailures(after, email))
        && !distributedAccountAttack(accountFailures(before, email)))
      notifications.notifyAdmins(
          "Wykryto rozproszone próby logowania na konto " + email + ".", "account_attack");
    List<LoginAttempt> ipFailures =
        after.stream().filter(a -> !a.success && ip.equals(a.ipAddress)).toList();
    if (!localNetwork(ip)
        && ipFailures.size() >= 6
        && ipFailures.stream().map(a -> a.email).distinct().count() >= 2) {
      boolean already =
          blocks
              .findById(ip)
              .map(b -> b.blockedUntil == null || b.blockedUntil.isAfter(now))
              .orElse(false);
      if (!already) {
        BlockedIp block = blocks.findById(ip).orElseGet(BlockedIp::new);
        block.ipAddress = ip;
        block.blockedUntil = now.plusSeconds(3600);
        block.reason = "Co najmniej 6 nieudanych prób logowania na co najmniej 2 konta w 15 minut.";
        block.updatedAt = now;
        blocks.save(block);
        notifications.notifyAdmins(
            "Zablokowano adres IP " + ip + " po próbach logowania na wiele kont.",
            "global_ip_attack");
      }
    }
  }

  // Za Nginx w Dockerze wszyscy lokalni użytkownicy mają ten sam adres bramki,
  // więc blokada całego IP zablokowałaby logowanie każdemu, także administratorowi.
  static boolean localNetwork(String ip) {
    try {
      InetAddress address = InetAddress.getByName(ip);
      return address.isLoopbackAddress() || address.isSiteLocalAddress();
    } catch (UnknownHostException error) {
      return false;
    }
  }

  static List<LoginAttempt> accountFailures(List<LoginAttempt> recent, String email) {
    Instant lastSuccess =
        recent.stream()
            .filter(a -> a.success && email.equalsIgnoreCase(a.email))
            .map(a -> a.attemptedAt)
            .max(Instant::compareTo)
            .orElse(Instant.EPOCH);
    return recent.stream()
        .filter(
            a ->
                !a.success && email.equalsIgnoreCase(a.email) && a.attemptedAt.isAfter(lastSuccess))
        .toList();
  }

  static boolean distributedAccountAttack(List<LoginAttempt> failed) {
    return failed.size() >= 4
        && failed.stream().map(a -> a.ipAddress).filter(Objects::nonNull).distinct().count() >= 2;
  }

  public record AttemptView(
      Long id, String email, String ipAddress, Instant attemptedAt, boolean success) {
    static AttemptView from(LoginAttempt attempt) {
      return new AttemptView(
          attempt.id, attempt.email, attempt.ipAddress, attempt.attemptedAt, attempt.success);
    }
  }

  public record BlockView(String ipAddress, Instant blockedUntil, String reason) {}

  public record SecurityView(List<AttemptView> attempts, List<BlockView> blockedIps) {}

  public record ActivityView(List<AttemptView> attempts, long totalFailures, long distinctIps) {}

  @Transactional(readOnly = true)
  public SecurityView overview() {
    List<BlockView> active =
        blocks.findAll().stream()
            .filter(
                block -> block.blockedUntil == null || block.blockedUntil.isAfter(Instant.now()))
            .sorted(Comparator.comparing(block -> block.ipAddress))
            .map(block -> new BlockView(block.ipAddress, block.blockedUntil, block.reason))
            .toList();
    return new SecurityView(
        attempts.findTop200ByOrderByAttemptedAtDesc().stream().map(AttemptView::from).toList(),
        active);
  }

  @Transactional(readOnly = true)
  public ActivityView details(String email, String ip) {
    List<LoginAttempt> recent =
        attempts.findByAttemptedAtAfterOrderByAttemptedAtDesc(Instant.now().minusSeconds(900));
    List<LoginAttempt> matching =
        recent.stream()
            .filter(a -> email == null || email.isBlank() || email.equalsIgnoreCase(a.email))
            .filter(a -> ip == null || ip.isBlank() || ip.equals(a.ipAddress))
            .toList();
    return new ActivityView(
        matching.stream().map(AttemptView::from).toList(),
        matching.stream().filter(a -> !a.success).count(),
        matching.stream().filter(a -> !a.success).map(a -> a.ipAddress).distinct().count());
  }

  @Transactional
  public void unblock(String ip) {
    blocks.deleteById(ip);
  }

  @Transactional
  public void resetForUser(String email) {
    List<LoginAttempt> history = attempts.findByEmailIgnoreCase(email);
    Set<String> addresses = new HashSet<>();
    history.stream()
        .map(attempt -> attempt.ipAddress)
        .filter(Objects::nonNull)
        .filter(ip -> !ip.isBlank())
        .forEach(addresses::add);
    blocks.deleteAllById(addresses);
    List<Notification> alerts =
        notificationRecords.findAll().stream()
            .filter(
                notification ->
                    ("account_attack".equals(notification.type)
                            && notification
                                .message
                                .toLowerCase(Locale.ROOT)
                                .contains(email.toLowerCase(Locale.ROOT)))
                        || ("global_ip_attack".equals(notification.type)
                            && addresses.stream()
                                .anyMatch(ip -> notification.message.contains(ip))))
            .toList();
    notificationRecords.deleteAll(alerts);
    attempts.deleteAll(history);
  }
}
