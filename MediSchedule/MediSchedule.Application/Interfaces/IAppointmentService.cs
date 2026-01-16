using MediSchedule.Application.DTOs;
using MediSchedule.Domain.Entities;

namespace MediSchedule.Application.Interfaces
{
    public interface IAppointmentService
    {
        Task<Appointment> ScheduleAppointmentAsync(CreateAppointmentDto dto);
    }
}