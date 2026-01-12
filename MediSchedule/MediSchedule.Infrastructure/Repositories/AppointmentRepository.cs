using MediSchedule.Application.Interfaces;
using MediSchedule.Domain.Entities;
using MediSchedule.Domain.Enums; // Potrzebne do AppointmentStatus
using MediSchedule.Infrastructure.Data;
using Microsoft.EntityFrameworkCore;

namespace MediSchedule.Infrastructure.Repositories
{
    public class AppointmentRepository : IAppointmentRepository
    {
        private readonly MediScheduleDbContext _context;

        public AppointmentRepository(MediScheduleDbContext context)
        {
            _context = context;
        }

        // 1. Sprawdzanie kolizji
        public async Task<bool> HasOverlapAsync(int doctorId, DateTime startTime, DateTime endTime)
        {
            return await _context.Appointments
                .AnyAsync(a => a.DoctorId == doctorId &&
                               a.Status != AppointmentStatus.Canceled &&
                               a.StartTime < endTime &&
                               a.StartTime.AddMinutes(a.DurationMinutes) > startTime);
        }

        // 2. Dodawanie wizyty
        public async Task AddAsync(Appointment appointment)
        {
            _context.Appointments.Add(appointment);
            await _context.SaveChangesAsync();
        }

        // 3. Pobieranie wizyty po ID
        public async Task<Appointment?> GetByIdAsync(int id)
        {
            return await _context.Appointments
                .Include(a => a.Doctor)
                .Include(a => a.Patient)
                .FirstOrDefaultAsync(a => a.Id == id);
        }

        // 4. Anulowanie wizyty 
        public async Task CancelAsync(int id)
        {
            var appointment = await _context.Appointments.FindAsync(id);
            if (appointment != null)
            {
                appointment.Status = AppointmentStatus.Canceled;
                await _context.SaveChangesAsync();
            }
        }
    }
}