using MediSchedule.Application.DTOs;
using MediSchedule.Domain.Entities;
using MediSchedule.Infrastructure.Data;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace MediSchedule.Api.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    public class DoctorsController : ControllerBase
    {
        private readonly MediScheduleDbContext _context;

        // Wstrzykiwanie DbContext przez konstruktor (Dependency Injection)
        public DoctorsController(MediScheduleDbContext context)
        {
            _context = context;
        }

        // GET: api/doctors
        [HttpGet]
        public async Task<ActionResult<IEnumerable<Doctor>>> GetDoctors()
        {
            // Zwracamy tylko aktywnych lekarzy
            var doctors = await _context.Doctors
                .Where(d => d.IsActive)
                .ToListAsync();

            return Ok(doctors);
        }

        // GET: api/doctors/5
        [HttpGet("{id}")]
        public async Task<ActionResult<Doctor>> GetDoctor(int id)
        {
            var doctor = await _context.Doctors.FindAsync(id);

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
            // Mapowanie DTO -> Encja
            var doctor = new Doctor
            {
                FirstName = dto.FirstName,
                LastName = dto.LastName,
                Specialization = dto.Specialization,
                BaseRate = dto.BaseRate,
                IsActive = true
            };

            _context.Doctors.Add(doctor);
            await _context.SaveChangesAsync();

            // Zwracamy 201 Created wraz z linkiem do nowego zasobu
            return CreatedAtAction(nameof(GetDoctor), new { id = doctor.Id }, doctor);
        }

        // DELETE: api/doctors/5
        [HttpDelete("{id}")]
        public async Task<IActionResult> DeleteDoctor(int id)
        {
            var doctor = await _context.Doctors.FindAsync(id);
            if (doctor == null)
            {
                return NotFound();
            }

            // Soft Delete - nie usuwamy rekordu, tylko oznaczamy jako nieaktywny
            doctor.IsActive = false;
            await _context.SaveChangesAsync();

            return NoContent(); // 204 No Content
        }
    }
}