using MediSchedule.Domain.Entities;
using Microsoft.EntityFrameworkCore;

namespace MediSchedule.Infrastructure.Data
{
    public class MediScheduleDbContext : DbContext
    {
        // Konstruktor przyjmujący opcje
        public MediScheduleDbContext(DbContextOptions<MediScheduleDbContext> options) : base(options)
        {
        }

        // Reprezentacja tabel w bazie danych
        public DbSet<Doctor> Doctors { get; set; }
        public DbSet<Patient> Patients { get; set; }
        public DbSet<Appointment> Appointments { get; set; }
        public DbSet<Schedule> Schedules { get; set; }

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            base.OnModelCreating(modelBuilder);

            // Konfiguracja dla wizyty
            modelBuilder.Entity<Appointment>()
                .Property(a => a.Price)
                .HasColumnType("decimal(18,2)");

            // Konfiguracja dla lekarza (stawka godzinowa)
            modelBuilder.Entity<Doctor>()
                .Property(d => d.BaseRate)
                .HasColumnType("decimal(18,2)");
        }
    }
}