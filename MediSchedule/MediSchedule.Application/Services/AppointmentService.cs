using MediSchedule.Application.DTOs;
using MediSchedule.Application.Interfaces;
using MediSchedule.Domain.Entities;

namespace MediSchedule.Application.Services
{
    public class AppointmentService : IAppointmentService
    {
        private readonly IAppointmentRepository _appointmentRepository;
        private readonly IDoctorRepository _doctorRepository;
        private readonly IScheduleRepository _scheduleRepository; // NOWE

        public AppointmentService(
            IAppointmentRepository appointmentRepository,
            IDoctorRepository doctorRepository,
            IScheduleRepository scheduleRepository) // Dodaj do konstruktora
        {
            _appointmentRepository = appointmentRepository;
            _doctorRepository = doctorRepository;
            _scheduleRepository = scheduleRepository;
        }

        public async Task<Appointment> ScheduleAppointmentAsync(CreateAppointmentDto dto)
        {
            var doctor = await _doctorRepository.GetByIdAsync(dto.DoctorId);
            if (doctor == null || !doctor.IsActive)
                throw new Exception("Lekarz nie istnieje lub jest nieaktywny.");

            // Sprawdź czy lekarz pracuje w ten dzień
            var dayOfWeek = dto.StartTime.DayOfWeek;
            var schedule = await _scheduleRepository.GetDoctorScheduleForDayAsync(dto.DoctorId, dayOfWeek);

            // Jeśli brak grafiku na ten dzień, lekarz nie przyjmuje
            if (schedule == null)
                throw new InvalidOperationException($"Lekarz nie przyjmuje w dniu: {dayOfWeek}");

            // Sprawdź godziny (czy mieści się w grafiku)
            var appointmentStart = TimeOnly.FromTimeSpan(dto.StartTime.TimeOfDay);
            var appointmentEnd = TimeOnly.FromTimeSpan(dto.StartTime.AddMinutes(dto.DurationMinutes).TimeOfDay);

            // Konwersja TimeSpan z bazy na TimeOnly
            var workStart = TimeOnly.FromTimeSpan(schedule.StartTime);
            var workEnd = TimeOnly.FromTimeSpan(schedule.EndTime);

            // Sprawdź czy wizyta mieści się w godzinach pracy
            if (appointmentStart < workStart || appointmentEnd > workEnd)
                throw new InvalidOperationException($"Wizyta poza godzinami pracy lekarza ({workStart}-{workEnd}).");

            // Sprawdź kolizje z innymi wizytami
            var endTime = dto.StartTime.AddMinutes(dto.DurationMinutes);
            var hasOverlap = await _appointmentRepository.HasOverlapAsync(dto.DoctorId, dto.StartTime, endTime);
            if (hasOverlap)
                throw new InvalidOperationException("Termin jest już zajęty przez inną wizytę.");

            // Oblicz cenę na podstawie stawki godzinowej lekarza
            decimal price = doctor.BaseRate * (dto.DurationMinutes / 60.0m);

            var appointment = new Appointment
            {
                DoctorId = dto.DoctorId,
                PatientId = dto.PatientId,
                StartTime = dto.StartTime,
                DurationMinutes = dto.DurationMinutes,
                Price = Math.Round(price, 2),
                Status = Domain.Enums.AppointmentStatus.Scheduled
            };

            await _appointmentRepository.AddAsync(appointment);

            return appointment;
        }
    }
}