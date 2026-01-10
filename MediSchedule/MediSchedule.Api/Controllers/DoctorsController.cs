using MediSchedule.Application.DTOs;
using MediSchedule.Application.Interfaces;
using MediSchedule.Domain.Entities;
using Microsoft.AspNetCore.Mvc;

namespace MediSchedule.Api.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    public class DoctorsController : ControllerBase
    {
        private readonly IDoctorRepository _repository;

        public DoctorsController(IDoctorRepository repository)
        {
            _repository = repository;
        }

        // GET: api/doctors
        [HttpGet]
        public async Task<ActionResult<IEnumerable<Doctor>>> GetDoctors()
        {
            var doctors = await _repository.GetAllAsync();
            return Ok(doctors);
        }

        // GET: api/doctors/5
        [HttpGet("{id}")]
        public async Task<ActionResult<Doctor>> GetDoctor(int id)
        {
            var doctor = await _repository.GetByIdAsync(id);
            if (doctor == null || !doctor.IsActive)
            {
                return NotFound();
            }

            return Ok(doctor);
        }

        // POST: api/doctors
        [HttpPost]
        public async Task<ActionResult<Doctor>> CreateDoctor(CreateDoctorDto dto)
        {
            var doctor = new Doctor
            {
                FirstName = dto.FirstName,
                LastName = dto.LastName,
                Specialization = dto.Specialization,
                BaseRate = dto.BaseRate,
                IsActive = true
            };

            await _repository.AddAsync(doctor);

            return CreatedAtAction(nameof(GetDoctor), new { id = doctor.Id }, doctor);
        }

        // DELETE: api/doctors/5
        [HttpDelete("{id}")]
        public async Task<IActionResult> DeleteDoctor(int id)
        {
            await _repository.DeleteAsync(id);
            return NoContent();
        }
    }
}