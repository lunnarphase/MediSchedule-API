package pl.medischedule.service;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.medischedule.domain.*;
import pl.medischedule.repository.*;
import pl.medischedule.security.CurrentUser;
import pl.medischedule.web.*;

@Service
@Transactional
public class DoctorService {
  public static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
  private final DoctorRepository doctors;
  private final AvailabilityRepository availability;
  private final ScheduleTemplateRepository templates;
  private final AppointmentRepository appointments;
  private final SpecializationRepository specializations;
  private final CurrentUser current;
  private final Views views;

  public DoctorService(
      DoctorRepository doctors,
      AvailabilityRepository availability,
      ScheduleTemplateRepository templates,
      AppointmentRepository appointments,
      SpecializationRepository specializations,
      CurrentUser current,
      Views views) {
    this.doctors = doctors;
    this.availability = availability;
    this.templates = templates;
    this.appointments = appointments;
    this.specializations = specializations;
    this.current = current;
    this.views = views;
  }

  public Doctor get(Long id) {
    return doctors.findById(id).orElseThrow(() -> new ApiException(404, "Nie znaleziono lekarza."));
  }

  public Doctor mine() {
    return doctors
        .findByUserId(current.require("doctor").id)
        .orElseThrow(() -> new ApiException(404, "Brak profilu lekarza."));
  }

  public List<Map<String, Object>> search(String query, Long specialization) {
    String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
    return doctors.findAll().stream()
        .filter(d -> !d.user.blocked)
        .filter(d -> d.user.role.name.equals("doctor"))
        .filter(
            d ->
                d.user.name.toLowerCase(Locale.ROOT).contains(q)
                    || d.specializations.stream()
                        .anyMatch(s -> s.name.toLowerCase(Locale.ROOT).contains(q)))
        .filter(
            d ->
                specialization == null
                    || d.specializations.stream().anyMatch(s -> s.id.equals(specialization)))
        .map(views::doctor)
        .toList();
  }

  public List<?> specializations() {
    return specializations.findAll().stream()
        .map(s -> Views.data("id", s.id, "name", s.name))
        .toList();
  }

  public List<String> slots(Long id, LocalDate date) {
    Doctor d = get(id);
    if (d.user.blocked || !d.user.role.name.equals("doctor") || date.isBefore(LocalDate.now(ZONE)))
      return List.of();
    List<Appointment> taken =
        appointments.overlaps(id, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    SortedSet<String> result = new TreeSet<>();
    LocalDateTime now = LocalDateTime.now(ZONE);
    for (var range :
        availability.findByDoctorIdAndDateBetweenOrderByDateAscStartTimeAsc(id, date, date)) {
      for (LocalDateTime start = date.atTime(range.startTime);
          !start.plusMinutes(d.visitDuration).isAfter(date.atTime(range.endTime));
          start = start.plusMinutes(d.visitDuration)) {
        LocalDateTime end = start.plusMinutes(d.visitDuration);
        final LocalDateTime slot = start;
        if (start.isAfter(now)
            && taken.stream().noneMatch(a -> slot.isBefore(a.endsAt) && end.isAfter(a.startsAt)))
          result.add(start.toLocalTime().toString());
      }
    }
    return List.copyOf(result);
  }

  public List<String> availableDates(Long id, LocalDate from, LocalDate to) {
    validateRange(from, to);
    List<String> result = new ArrayList<>();
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1))
      if (!slots(id, day).isEmpty()) result.add(day.toString());
    return result;
  }

  public static void validateRange(LocalDate from, LocalDate to) {
    if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 93)
      throw new ApiException(400, "Zakres dat musi obejmować od 1 do 94 dni.");
  }

  public List<Map<String, Object>> week(LocalDate from, LocalDate to) {
    validateRange(from, to);
    return availability
        .findByDoctorIdAndDateBetweenOrderByDateAscStartTimeAsc(mine().id, from, to)
        .stream()
        .map(this::rangeView)
        .toList();
  }

  private Map<String, Object> rangeView(Availability a) {
    return Views.data(
        "id",
        a.id,
        "date",
        a.date,
        "startTime",
        a.startTime.toString(),
        "endTime",
        a.endTime.toString());
  }

  public List<Map<String, Object>> saveWeek(LocalDate from, LocalDate to, List<Range> ranges) {
    validateRange(from, to);
    if (from.isBefore(LocalDate.now(ZONE)))
      throw new ApiException(400, "Nie można zmieniać grafiku z przeszłości.");
    Doctor d = doctors.lockById(mine().id).orElseThrow();
    if (ranges == null || ranges.size() > 300) throw new ApiException(400, "Nieprawidłowy grafik.");
    for (Range r : ranges) {
      if (r == null
          || r.date == null
          || r.startTime == null
          || r.endTime == null
          || r.date.isBefore(from)
          || r.date.isAfter(to)
          || !r.startTime.isBefore(r.endTime)
          || r.startTime.getSecond() != 0
          || r.endTime.getSecond() != 0
          || r.startTime.getNano() != 0
          || r.endTime.getNano() != 0)
        throw new ApiException(400, "Nieprawidłowy przedział grafiku.");
    }
    var sorted =
        ranges.stream()
            .sorted(Comparator.comparing(Range::date).thenComparing(Range::startTime))
            .toList();
    for (int i = 1; i < sorted.size(); i++) {
      Range a = sorted.get(i - 1), b = sorted.get(i);
      if (a.date.equals(b.date) && a.endTime.isAfter(b.startTime))
        throw new ApiException(409, "Przedziały grafiku nakładają się.");
    }
    for (Appointment a :
        appointments.overlaps(d.id, from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
      if (ranges.stream()
          .noneMatch(
              r ->
                  r.date.equals(a.date)
                      && !r.startTime.isAfter(a.time)
                      && !r.endTime.isBefore(a.endsAt.toLocalTime())))
        throw new ApiException(
            409,
            "Grafik musi obejmować istniejące wizyty. Najpierw anuluj lub przenieś rezerwację.");
    }
    availability.deleteAll(
        availability.findByDoctorIdAndDateBetweenOrderByDateAscStartTimeAsc(d.id, from, to));
    availability.flush();
    for (Range r : ranges) {
      Availability a = new Availability();
      a.doctor = d;
      a.date = r.date;
      a.startTime = r.startTime;
      a.endTime = r.endTime;
      availability.save(a);
    }
    return week(from, to);
  }

  public Object profile() {
    return views.doctor(mine());
  }

  public Object saveProfile(String bio, BigDecimal price, int duration) {
    if (bio == null
        || bio.length() > 5000
        || price == null
        || price.signum() < 0
        || price.compareTo(new BigDecimal("100000")) > 0
        || duration < 10
        || duration > 240)
      throw new ApiException(400, "Sprawdź opis, cenę i czas wizyty (10–240 minut).");
    Doctor d = doctors.lockById(mine().id).orElseThrow();
    d.bio = bio.trim();
    d.visitPrice = price;
    d.visitDuration = duration;
    return views.doctor(d);
  }

  public List<?> templates() {
    return templates.findByDoctorIdOrderByNameAsc(mine().id).stream()
        .map(this::templateView)
        .toList();
  }

  public Object saveTemplate(String name, LocalTime start, LocalTime end) {
    if (name == null
        || name.isBlank()
        || name.length() > 100
        || start == null
        || end == null
        || !start.isBefore(end)) throw new ApiException(400, "Nieprawidłowy szablon.");
    ScheduleTemplate t = new ScheduleTemplate();
    t.doctor = mine();
    t.name = name.trim();
    t.startTime = start;
    t.endTime = end;
    return templateView(templates.save(t));
  }

  public void deleteTemplate(Long id) {
    var t = templates.findById(id).orElseThrow(() -> new ApiException(404, "Brak szablonu."));
    if (!t.doctor.id.equals(mine().id)) throw new ApiException(403, "Brak dostępu.");
    templates.delete(t);
  }

  private Map<String, Object> templateView(ScheduleTemplate t) {
    return Views.data(
        "id",
        t.id,
        "name",
        t.name,
        "startTime",
        t.startTime.toString(),
        "endTime",
        t.endTime.toString());
  }

  public record Range(LocalDate date, LocalTime startTime, LocalTime endTime) {}
}
