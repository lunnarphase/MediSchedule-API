using MediSchedule.Application.DTOs;
using MediSchedule.Application.Interfaces;
using MediSchedule.Application.Services;
using Microsoft.AspNetCore.Mvc;

namespace MediSchedule.Api.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    public class AppointmentsController : ControllerBase
    {
        private readonly IAppointmentService _appointmentService;
        private readonly IAppointmentRepository _appointmentRepository;

        public AppointmentsController(IAppointmentService appointmentService, IAppointmentRepository appointmentRepository)
        {
            _appointmentService = appointmentService;
            _appointmentRepository = appointmentRepository;
        }

        // POST: api/appointments
        [HttpPost]
        public async Task<ActionResult<AppointmentDto>> CreateAppointment(CreateAppointmentDto dto)
        {
            try
            {
                
                var appointment = await _appointmentService.ScheduleAppointmentAsync(dto);

                // Mapowanie Encja -> DTO 
                var resultDto = new AppointmentDto
                {
                    Id = appointment.Id,
                    DoctorId = appointment.DoctorId,
                    DoctorName = "Sprawdź GETem", // Debug info
                    PatientId = appointment.PatientId,
                    PatientName = "Sprawdź GETem",
                    StartTime = appointment.StartTime,
                    DurationMinutes = appointment.DurationMinutes,
                    Price = appointment.Price,
                    Status = appointment.Status.ToString()
                };

                return CreatedAtAction(nameof(GetAppointment), new { id = appointment.Id }, resultDto);
            }
            catch (InvalidOperationException ex) // Np. kolizja terminów
            {
                return Conflict(new { message = ex.Message });
            }
            catch (Exception ex) // Np. brak lekarza
            {
                return BadRequest(new { message = ex.Message });
            }
        }

        // GET: api/appointments/5
        [HttpGet("{id}")]
        public async Task<ActionResult<AppointmentDto>> GetAppointment(int id)
        {
            var appointment = await _appointmentRepository.GetByIdAsync(id);

            if (appointment == null)
            {
                return NotFound();
            }

            var dto = new AppointmentDto
            {
                Id = appointment.Id,
                DoctorId = appointment.DoctorId,
                DoctorName = $"{appointment.Doctor.FirstName} {appointment.Doctor.LastName}",
                PatientId = appointment.PatientId,
                PatientName = $"{appointment.Patient.FirstName} {appointment.Patient.LastName}",
                StartTime = appointment.StartTime,
                DurationMinutes = appointment.DurationMinutes,
                Price = appointment.Price,
                Status = appointment.Status.ToString()
            };

            return Ok(dto);
        }

        // DELETE: api/appointments/5 (Anulowanie)
        [HttpDelete("{id}")]
        public async Task<IActionResult> CancelAppointment(int id)
        {
            await _appointmentRepository.CancelAsync(id);
            return NoContent();
        }
    }
}