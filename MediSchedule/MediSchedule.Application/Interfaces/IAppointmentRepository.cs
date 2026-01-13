using MediSchedule.Domain.Entities;

namespace MediSchedule.Application.Interfaces
{
    public interface IAppointmentRepository
    {
        Task<bool> HasOverlapAsync(int doctorId, DateTime startTime, DateTime endTime);

        Task AddAsync(Appointment appointment);
        Task<Appointment?> GetByIdAsync(int id);
        Task CancelAsync(int id);
        Task<IEnumerable<Appointment>> GetDoctorAppointmentsAsync(int doctorId, DateTime start, DateTime end);
        Task<IEnumerable<Appointment>> GetAllAsync();

    }
}