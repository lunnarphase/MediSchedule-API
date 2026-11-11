using MediSchedule.Application.Interfaces;
using MediSchedule.Domain.Entities;
using MediSchedule.Infrastructure.Data;
using Microsoft.EntityFrameworkCore;

namespace MediSchedule.Infrastructure.Repositories
{
    public class ScheduleRepository : IScheduleRepository
    {
        private readonly MediScheduleDbContext _context;

        public ScheduleRepository(MediScheduleDbContext context)
        {
            _context = context;
        }

        public async Task AddAsync(Schedule schedule)
        {
            _context.Schedules.Add(schedule);
            await _context.SaveChangesAsync();
        }

        public async Task<IEnumerable<Schedule>> GetDoctorSchedulesAsync(int doctorId)
        {
            return await _context.Schedules
                .Where(s => s.DoctorId == doctorId)
                .ToListAsync();
        }

        public async Task<Schedule?> GetDoctorScheduleForDayAsync(int doctorId, DayOfWeek day)
        {
            return await _context.Schedules
                .FirstOrDefaultAsync(s => s.DoctorId == doctorId && s.DayOfWeek == day);
        }
    }
}