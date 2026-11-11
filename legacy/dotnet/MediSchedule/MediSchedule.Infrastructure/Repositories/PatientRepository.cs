using MediSchedule.Application.Interfaces;
using MediSchedule.Domain.Entities;
using MediSchedule.Infrastructure.Data;
using Microsoft.EntityFrameworkCore;

namespace MediSchedule.Infrastructure.Repositories
{
    public class PatientRepository : IPatientRepository
    {
        private readonly MediScheduleDbContext _context;

        public PatientRepository(MediScheduleDbContext context)
        {
            _context = context;
        }

        public async Task<IEnumerable<Patient>> GetAllAsync()
        {
            return await _context.Patients
                .Where(p => p.IsActive)
                .ToListAsync();
        }

        public async Task<Patient?> GetByIdAsync(int id)
        {
            return await _context.Patients.FindAsync(id);
        }

        public async Task AddAsync(Patient patient)
        {
            _context.Patients.Add(patient);
            await _context.SaveChangesAsync();
        }

        public async Task UpdateAsync(Patient patient)
        {
            _context.Patients.Update(patient);
            await _context.SaveChangesAsync();
        }

        public async Task DeleteAsync(int id)
        {
            var patient = await _context.Patients.FindAsync(id);
            if (patient != null)
            {
                patient.IsActive = false; // Soft delete
                await _context.SaveChangesAsync();
            }
        }
    }
}