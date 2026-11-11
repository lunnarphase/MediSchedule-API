using MediSchedule.Application.Interfaces;
using MediSchedule.Domain.Entities;
using MediSchedule.Infrastructure.Data;
using Microsoft.EntityFrameworkCore;

namespace MediSchedule.Infrastructure.Repositories
{
    public class DoctorRepository : IDoctorRepository
    {
        private readonly MediScheduleDbContext _context;

        public DoctorRepository(MediScheduleDbContext context)
        {
            _context = context;
        }

        public async Task<IEnumerable<Doctor>> GetAllAsync()
        {
            return await _context.Doctors.Where(d => d.IsActive).ToListAsync();
        }

        public async Task<Doctor?> GetByIdAsync(int id)
        {
            return await _context.Doctors.FindAsync(id);
        }

        public async Task AddAsync(Doctor doctor)
        {
            _context.Doctors.Add(doctor);
            await _context.SaveChangesAsync();
        }

        public async Task UpdateAsync(Doctor doctor)
        {
            _context.Doctors.Update(doctor);
            await _context.SaveChangesAsync();
        }

        public async Task DeleteAsync(int id)
        {
            var doctor = await _context.Doctors.FindAsync(id);
            if (doctor != null)
            {
                doctor.IsActive = false; // Soft delete
                await _context.SaveChangesAsync();
            }
        }
    }
}