using MediSchedule.Application.DTOs;
using MediSchedule.Application.Interfaces;
using MediSchedule.Domain.Entities;
using Microsoft.AspNetCore.Mvc;

namespace MediSchedule.Api.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    public class SchedulesController : ControllerBase
    {
        private readonly IScheduleRepository _repository;

        public SchedulesController(IScheduleRepository repository)
        {
            _repository = repository;
        }

        [HttpPost]
        public async Task<IActionResult> CreateSchedule(CreateScheduleDto dto)
        {
            // Walidacja logiczna
            if (dto.StartTime >= dto.EndTime)
                return BadRequest("Godzina rozpoczęcia musi być przed godziną zakończenia.");

            // Sprawdź czy lekarz już nie ma grafiku na ten dzień
            var existing = await _repository.GetDoctorScheduleForDayAsync(dto.DoctorId, dto.DayOfWeek);
            if (existing != null)
                return Conflict($"Lekarz ma już zdefiniowany grafik na {dto.DayOfWeek}.");

            var schedule = new Schedule
            {
                DoctorId = dto.DoctorId,
                DayOfWeek = dto.DayOfWeek,
                StartTime = dto.StartTime,
                EndTime = dto.EndTime
            };

            await _repository.AddAsync(schedule);
            return Ok(schedule); // Uproszczenie: zwracamy encję
        }

        [HttpGet("{doctorId}")]
        public async Task<ActionResult<IEnumerable<ScheduleDto>>> GetDoctorSchedules(int doctorId)
        {
            var schedules = await _repository.GetDoctorSchedulesAsync(doctorId);

            // Mapowanie Encja -> DTO
            var dtos = schedules.Select(s => new ScheduleDto
            {
                Id = s.Id,
                DoctorId = s.DoctorId,
                DayOfWeek = s.DayOfWeek.ToString(), // "Monday", "Tuesday" itp. - czytelniej niż 1, 2
                StartTime = s.StartTime,
                EndTime = s.EndTime
            });

            return Ok(dtos);
        }
    }
}