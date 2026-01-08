using MediSchedule.Domain.Entities;
using Microsoft.EntityFrameworkCore;

namespace MediSchedule.Infrastructure.Data
{
    public class MediScheduleDbContext : DbContext
    {
        // Konstruktor przyjmujący opcje (np. connection string)
        public MediScheduleDbContext(DbContextOptions<MediScheduleDbContext> options) : base(options)
        {
        }

        // Reprezentacja tabel w bazie danych
        public DbSet<Doctor> Doctors { get; set; }
        public DbSet<Patient> Patients { get; set; }
        public DbSet<Appointment> Appointments { get; set; }

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            base.OnModelCreating(modelBuilder);

            // Konfiguracja precyzji dla ceny poniewaz domyślnie EF może nie ustawić odpowiedniego typu w bazie danych
            modelBuilder.Entity<Appointment>()
                .Property(a => a.Price)
                .HasColumnType("decimal(18,2)");
        }
    }
}