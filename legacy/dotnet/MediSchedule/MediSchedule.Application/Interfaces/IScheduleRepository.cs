using MediSchedule.Domain.Entities;

namespace MediSchedule.Application.Interfaces
{
    public interface IScheduleRepository
    {
        Task AddAsync(Schedule schedule);
        Task<IEnumerable<Schedule>> GetDoctorSchedulesAsync(int doctorId);
        Task<Schedule?> GetDoctorScheduleForDayAsync(int doctorId, DayOfWeek day);
    }
}