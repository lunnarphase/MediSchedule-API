using MediSchedule.Application.DTOs;
using MediSchedule.Domain.Entities;
using MediSchedule.Infrastructure.Data;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace MediSchedule.Api.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    public class PatientsController : ControllerBase
    {
        private readonly MediScheduleDbContext _context;

        public PatientsController(MediScheduleDbContext context)
        {
            _context = context;
        }

        // GET: api/patients
        [HttpGet]
        public async Task<ActionResult<IEnumerable<PatientDto>>> GetPatients()
        {
            var patients = await _context.Patients
                .Where(p => p.IsActive)
                .Select(p => new PatientDto // Ręczne mapowanie Encja -> DTO
                {
                    Id = p.Id,
                    FirstName = p.FirstName,
                    LastName = p.LastName,
                    Email = p.Email,
                    Pesel = p.Pesel,
                    IsActive = p.IsActive
                })
                .ToListAsync();

            return Ok(patients);
        }

        // GET: api/patients/5
        [HttpGet("{id}")]
        public async Task<ActionResult<PatientDto>> GetPatient(int id)
        {
            var patient = await _context.Patients.FindAsync(id);

            if (patient == null || !patient.IsActive)
            {
                return NotFound();
            }

            var dto = new PatientDto
            {
                Id = patient.Id,
                FirstName = patient.FirstName,
                LastName = patient.LastName,
                Email = patient.Email,
                Pesel = patient.Pesel,
                IsActive = patient.IsActive
            };

            return Ok(dto);
        }

        // POST: api/patients
        [HttpPost]
        public async Task<ActionResult<PatientDto>> CreatePatient(CreatePatientDto dto)
        {
            if (await _context.Patients.AnyAsync(p => p.Pesel == dto.Pesel))
            {
                return Conflict("Pacjent o podanym numerze PESEL już istnieje.");
            }

            var patient = new Patient
            {
                FirstName = dto.FirstName,
                LastName = dto.LastName,
                Email = dto.Email,
                Pesel = dto.Pesel,
                IsActive = true
            };

            _context.Patients.Add(patient);
            await _context.SaveChangesAsync();

            // Zwracamy DTO zamiast encji
            var resultDto = new PatientDto
            {
                Id = patient.Id,
                FirstName = patient.FirstName,
                LastName = patient.LastName,
                Email = patient.Email,
                Pesel = patient.Pesel,
                IsActive = true
            };

            return CreatedAtAction(nameof(GetPatient), new { id = patient.Id }, resultDto);
        }

        // DELETE: api/patients/5
        [HttpDelete("{id}")]
        public async Task<IActionResult> DeletePatient(int id)
        {
            var patient = await _context.Patients.FindAsync(id);
            if (patient == null)
            {
                return NotFound();
            }

            patient.IsActive = false;
            await _context.SaveChangesAsync();

            return NoContent();
        }
    }
}