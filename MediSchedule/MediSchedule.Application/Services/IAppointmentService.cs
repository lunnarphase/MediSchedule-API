using MediSchedule.Application.DTOs;
using MediSchedule.Domain.Entities;

namespace MediSchedule.Application.Services
{
    public interface IAppointmentService
    {
        Task<Appointment> ScheduleAppointmentAsync(CreateAppointmentDto dto);
    }
}