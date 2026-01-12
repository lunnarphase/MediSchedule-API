using MediSchedule.Application.DTOs;
using MediSchedule.Application.Interfaces;
using MediSchedule.Domain.Entities;

namespace MediSchedule.Application.Services
{
    public class AppointmentService : IAppointmentService
    {
        private readonly IAppointmentRepository _appointmentRepository;
        private readonly IDoctorRepository _doctorRepository; // Potrzebny do pobrania stawki lekarza

        public AppointmentService(IAppointmentRepository appointmentRepository, IDoctorRepository doctorRepository)
        {
            _appointmentRepository = appointmentRepository;
            _doctorRepository = doctorRepository;
        }

        public async Task<Appointment> ScheduleAppointmentAsync(CreateAppointmentDto dto)
        {
            // 1. Pobierz lekarza, żeby sprawdzić stawkę
            var doctor = await _doctorRepository.GetByIdAsync(dto.DoctorId);
            if (doctor == null || !doctor.IsActive)
                throw new Exception("Lekarz nie istnieje lub jest nieaktywny.");

            // 2. Oblicz datę zakończenia wizyty
            var endTime = dto.StartTime.AddMinutes(dto.DurationMinutes);

            // 3. Sprawdź KOLIZJĘ terminów (Reguła biznesowa nr 1)
            var hasOverlap = await _appointmentRepository.HasOverlapAsync(dto.DoctorId, dto.StartTime, endTime);
            if (hasOverlap)
                throw new InvalidOperationException("Termin jest już zajęty.");

            // 4. Oblicz CENĘ (Reguła biznesowa nr 2: Czas * Stawka)
            // BaseRate = stawka za godzinę (60 min)
            decimal price = doctor.BaseRate * (dto.DurationMinutes / 60.0m);

            // 5. Utwórz encję
            var appointment = new Appointment
            {
                DoctorId = dto.DoctorId,
                PatientId = dto.PatientId,
                StartTime = dto.StartTime,
                DurationMinutes = dto.DurationMinutes,
                Price = Math.Round(price, 2), // Zaokrąglenie do groszy
                Status = Domain.Enums.AppointmentStatus.Scheduled
            };

            // 6. Zapis w bazie
            await _appointmentRepository.AddAsync(appointment);

            return appointment;
        }
    }
}