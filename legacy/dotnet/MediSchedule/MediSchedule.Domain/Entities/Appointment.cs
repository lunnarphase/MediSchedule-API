using MediSchedule.Domain.Enums;

namespace MediSchedule.Domain.Entities
{
    public class Appointment
    {
        public int Id { get; set; }
        public DateTime StartTime { get; set; } // Data i godzina rozpoczęcia wizyty
        public int DurationMinutes { get; set; } // Czas trwania w minutach (np. 15, 30, 60)
        public decimal Price { get; set; } // Cena wizyty (wyliczana w momencie rezerwacji)
        public AppointmentStatus Status { get; set; } = AppointmentStatus.Scheduled; // Domyślny status przy utworzeniu to 'Scheduled'


        // ---------- RELACJE (Foreign Keys) -----------
        public int DoctorId { get; set; } // Klucz obcy do lekarza
        public Doctor Doctor { get; set; } = default!; // Właściwość nawigacyjna (pozwala pobrać dane lekarza z obiektu wizyty)
        public int PatientId { get; set; } // Klucz obcy do pacjenta
        public Patient Patient { get; set; } = default!;  // Właściwość nawigacyjna (pozwala pobrać dane pacjenta z obiektu wizyty)
    }
}