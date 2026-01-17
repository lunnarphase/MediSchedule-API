using MediSchedule.Application.Interfaces;
using MediSchedule.Application.Services;
using MediSchedule.Infrastructure.Data;
using MediSchedule.Infrastructure.Repositories;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();

// --- REJESTRACJA SERWISÓW (DEPENDENCY INJECTION) ---
builder.Services.AddScoped<IDoctorRepository, DoctorRepository>();
builder.Services.AddScoped<IAppointmentRepository, AppointmentRepository>();
builder.Services.AddScoped<IPatientRepository, PatientRepository>(); // <-- DODA£EM TO (Naprawia b³¹d)
builder.Services.AddScoped<IScheduleRepository, ScheduleRepository>();

builder.Services.AddScoped<IAppointmentService, AppointmentService>();
// ---------------------------------------------------

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();


// --- KONFIGURACJA BAZY DANYCH ---
builder.Services.AddDbContext<MediScheduleDbContext>(options =>
    options.UseSqlServer(builder.Configuration.GetConnectionString("DefaultConnection")));
// ------------------------------

var app = builder.Build();

// --- AUTOMATYCZNA MIGRACJA BAZY DANYCH ---
using (var scope = app.Services.CreateScope())
{
    var services = scope.ServiceProvider;
    try
    {
        var context = services.GetRequiredService<MediScheduleDbContext>();

        context.Database.Migrate();
        Console.WriteLine("Baza danych zostaa pomylnie zmigrowana.");
    }
    catch (Exception ex)
    {
        Console.WriteLine($"Wystpi bd podczas migracji bazy: {ex.Message}");
    }
}
// -----------------------------------------

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();

app.UseAuthorization();

app.MapControllers();

app.Run();