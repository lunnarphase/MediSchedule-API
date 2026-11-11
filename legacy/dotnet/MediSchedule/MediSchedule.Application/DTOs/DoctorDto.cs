namespace MediSchedule.Application.DTOs
{
    // Ten obiekt zwracamy klientowi, gdy pyta o listę lekarzy
    public class DoctorDto
    {
        public int Id { get; set; }
        public string FirstName { get; set; } = default!;
        public string LastName { get; set; } = default!;
        public string Specialization { get; set; } = default!;
        public decimal BaseRate { get; set; }
        public bool IsActive { get; set; }
    }
}