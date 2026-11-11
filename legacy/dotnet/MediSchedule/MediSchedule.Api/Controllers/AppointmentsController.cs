using Microsoft.AspNetCore.Mvc;
using MediSchedule.Application.DTOs;
using MediSchedule.Application.Interfaces; 
using MediSchedule.Application.Services;

namespace MediSchedule.Api.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    public class AppointmentsController : ControllerBase
    {
        private readonly IAppointmentService _appointmentService;
        private readonly IAppointmentRepository _appointmentRepository;
        private readonly IDoctorRepository _doctorRepository;
        private readonly IPatientRepository _patientRepository;

        public AppointmentsController( 
            IAppointmentService appointmentService, 
            IAppointmentRepository appointmentRepository,
            IDoctorRepository doctorRepository,
            IPatientRepository patientRepository)
        {
            _appointmentService = appointmentService;
            _appointmentRepository = appointmentRepository;
            _doctorRepository = doctorRepository;
            _patientRepository = patientRepository;
        }

        // POST: api/appointments
        [HttpPost]
        public async Task<ActionResult<AppointmentDto>> CreateAppointment(CreateAppointmentDto dto)
        {
            try
            {
                var appointment = await _appointmentService.ScheduleAppointmentAsync(dto);

                // Pobieramy dane, aby wyświetlić nazwiska
                var doctor = await _doctorRepository.GetByIdAsync(appointment.DoctorId);
                var patient = await _patientRepository.GetByIdAsync(appointment.PatientId);

                // Mapowanie encji domenowej () na obiekt DTO w celu ukrycia szczegółów wewnętrznej implementacji 
                var resultDto = new AppointmentDto
                {
                    Id = appointment.Id,
                    DoctorId = appointment.DoctorId,
                    DoctorName = doctor != null ? $"{doctor.FirstName} {doctor.LastName}" : "Unknown",
                    PatientId = appointment.PatientId,
                    PatientName = patient != null ? $"{patient.FirstName} {patient.LastName}" : "Unknown",
                    StartTime = appointment.StartTime,
                    DurationMinutes = appointment.DurationMinutes,
                    Price = appointment.Price,
                    Status = appointment.Status.ToString()
                };

                return CreatedAtAction(nameof(GetAppointment), new { id = appointment.Id }, resultDto);
            }
            catch (InvalidOperationException ex)
            {
                return Conflict(new { message = ex.Message });
            }
            catch (Exception ex)
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

            var doctor = await _doctorRepository.GetByIdAsync(appointment.DoctorId);
            var patient = await _patientRepository.GetByIdAsync(appointment.PatientId);

            var dto = new AppointmentDto
            {
                Id = appointment.Id,
                DoctorId = appointment.DoctorId,
                DoctorName = doctor != null ? $"{doctor.FirstName} {doctor.LastName}" : "Unknown",
                PatientId = appointment.PatientId,
                PatientName = patient != null ? $"{patient.FirstName} {patient.LastName}" : "Unknown",
                StartTime = appointment.StartTime,
                DurationMinutes = appointment.DurationMinutes,
                Price = appointment.Price,
                Status = appointment.Status.ToString()
            };

            return dto;
        }

        // GET: api/appointments
        [HttpGet]
        public async Task<ActionResult<IEnumerable<AppointmentDto>>> GetAppointments()
        {
            var appointments = await _appointmentRepository.GetAllAsync();
            var dtos = new List<AppointmentDto>();

            foreach (var app in appointments)
            {
                var doctor = await _doctorRepository.GetByIdAsync(app.DoctorId);
                var patient = await _patientRepository.GetByIdAsync(app.PatientId);

                dtos.Add(new AppointmentDto
                {
                    Id = app.Id,
                    DoctorId = app.DoctorId,
                    DoctorName = doctor != null ? $"{doctor.FirstName} {doctor.LastName}" : "Unknown",
                    PatientId = app.PatientId,
                    PatientName = patient != null ? $"{patient.FirstName} {patient.LastName}" : "Unknown",
                    StartTime = app.StartTime,
                    DurationMinutes = app.DurationMinutes,
                    Price = app.Price,
                    Status = app.Status.ToString()
                });
            }

            return Ok(dtos);
        }

        // DELETE: api/appointments/5
        [HttpDelete("{id}")]
        public async Task<IActionResult> DeleteAppointment(int id)
        {
            try
            {
                await _appointmentRepository.CancelAsync(id);
                return NoContent();
            }
            catch (Exception)
            {
                return NotFound();
            }
        }

        // PUT: api/appointments/5/complete
        [HttpPut("{id}/complete")]
        public async Task<IActionResult> CompleteAppointment(int id)
        {
            var appointment = await _appointmentRepository.GetByIdAsync(id);
            if (appointment == null) return NotFound();

            await _appointmentRepository.CompleteAsync(id);
            return NoContent();
        }
    }
}