namespace MediSchedule.Application.DTOs
{
    public class PatientDto
    {
        public int Id { get; set; }
        public string FirstName { get; set; } = default!;
        public string LastName { get; set; } = default!;
        public string Email { get; set; } = default!;
        public string Pesel { get; set; } = default!;
        public bool IsActive { get; set; }
    }
}