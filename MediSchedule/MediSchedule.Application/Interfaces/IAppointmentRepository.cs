using MediSchedule.Domain.Entities;

namespace MediSchedule.Application.Interfaces
{
    public interface IAppointmentRepository
    {
        Task<bool> HasOverlapAsync(int doctorId, DateTime startTime, DateTime endTime);

        Task AddAsync(Appointment appointment);
        Task<Appointment?> GetByIdAsync(int id);
        Task CancelAsync(int id);
    }
}