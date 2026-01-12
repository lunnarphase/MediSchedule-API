using System.ComponentModel.DataAnnotations;

namespace MediSchedule.Application.DTOs
{
    public class CreateAppointmentDto
    {
        [Required]
        public int DoctorId { get; set; }

        [Required]
        public int PatientId { get; set; }

        [Required]
        public DateTime StartTime { get; set; }

        [Range(15, 120, ErrorMessage = "Wizyta musi trwać od 15 do 120 minut")]
        public int DurationMinutes { get; set; }
    }
}