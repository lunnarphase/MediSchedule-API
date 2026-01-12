namespace MediSchedule.Application.DTOs
{
    public class AppointmentDto
    {
        public int Id { get; set; }
        public int DoctorId { get; set; }
        public string DoctorName { get; set; } = default!; 
        public int PatientId { get; set; }
        public string PatientName { get; set; } = default!;

        public DateTime StartTime { get; set; }
        public int DurationMinutes { get; set; }
        public decimal Price { get; set; }
        public string Status { get; set; } = default!;
    }
}