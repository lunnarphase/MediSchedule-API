using MediSchedule.Application.Interfaces;
using MediSchedule.Domain.Entities;
using MediSchedule.Domain.Enums;
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

        public async Task<bool> HasOverlapAsync(int doctorId, DateTime startTime, DateTime endTime)
        {
            return await _context.Appointments
                .AnyAsync(a => a.DoctorId == doctorId &&
                               a.Status != AppointmentStatus.Canceled &&
                               a.StartTime < endTime &&
                               a.StartTime.AddMinutes(a.DurationMinutes) > startTime);
        }

        public async Task AddAsync(Appointment appointment)
        {
            _context.Appointments.Add(appointment);
            await _context.SaveChangesAsync();
        }

        public async Task<Appointment?> GetByIdAsync(int id)
        {
            return await _context.Appointments
                .Include(a => a.Doctor)
                .Include(a => a.Patient)
                .FirstOrDefaultAsync(a => a.Id == id);
        }

        public async Task CancelAsync(int id)
        {
            var appointment = await _context.Appointments.FindAsync(id);
            if (appointment != null)
            {
                appointment.Status = AppointmentStatus.Canceled;
                await _context.SaveChangesAsync();
            }
        }

        public async Task<IEnumerable<Appointment>> GetDoctorAppointmentsAsync(int doctorId, DateTime start, DateTime end)
        {
            return await _context.Appointments
               .Where(a => a.DoctorId == doctorId &&
                           a.Status != AppointmentStatus.Canceled &&
                           a.StartTime < end &&
                           a.StartTime.AddMinutes(a.DurationMinutes) > start)
               .ToListAsync();
        }

        public async Task<IEnumerable<Appointment>> GetAllAsync()
        {
            return await _context.Appointments
                .Include(a => a.Doctor)  // Dane lekarza
                .Include(a => a.Patient) // Dane pacjenta
                .OrderByDescending(a => a.StartTime) // Najnowsze na górze
                .ToListAsync();
        }
    }
}