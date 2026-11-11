namespace MediSchedule.Domain.Entities
{
    public class Doctor
    {
        public int Id { get; set; }
        public string FirstName { get; set; } = default!;
        public string LastName { get; set; } = default!;
        public string Specialization { get; set; } = default!;
        public decimal BaseRate { get; set; }  // Stawka godzinowa / za wizytę
        public bool IsActive { get; set; } = true;  // Soft delete - zamiast usuwać z bazy, będziemy deaktywować
        public ICollection<Schedule> Schedules { get; set; } = new List<Schedule>();
    }
}