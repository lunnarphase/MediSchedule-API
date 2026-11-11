using MediSchedule.Application.Interfaces;
using Microsoft.AspNetCore.Mvc;

namespace MediSchedule.Api.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    public class AvailabilityController : ControllerBase
    {
        private readonly IAppointmentRepository _appointmentRepository;

        public AvailabilityController(IAppointmentRepository appointmentRepository)
        {
            _appointmentRepository = appointmentRepository;
        }

        // GET: api/availability/check?doctorId=1&startTime=2026-02-15T10:00:00&durationMinutes=60
        [HttpGet("check")]
        public async Task<ActionResult<bool>> CheckAvailability(
            [FromQuery] int doctorId,
            [FromQuery] DateTime startTime,
            [FromQuery] int durationMinutes)
        {
            if (durationMinutes < 15 || durationMinutes > 120)
                return BadRequest("Czas trwania musi wynosić 15-120 minut.");

            var endTime = startTime.AddMinutes(durationMinutes);

            var hasOverlap = await _appointmentRepository.HasOverlapAsync(doctorId, startTime, endTime);

            // Jeśli jest overlap (kolizja), to IsAvailable = false
            return Ok(new { IsAvailable = !hasOverlap });
        }

        // Pobierz zajęte sloty lekarza na dany dzień (dla UI kalendarza)
        [HttpGet("slots/{doctorId}/{date}")]
        public async Task<ActionResult> GetTakenSlots(int doctorId, DateTime date)
        {
            var startOfDay = date.Date; // 00:00
            var endOfDay = date.Date.AddDays(1); // 00:00 następnego dnia

            var appointments = await _appointmentRepository.GetDoctorAppointmentsAsync(doctorId, startOfDay, endOfDay);

            var slots = appointments.Select(a => new
            {
                From = a.StartTime,
                To = a.StartTime.AddMinutes(a.DurationMinutes)
            });

            return Ok(slots);
        }
    }
}