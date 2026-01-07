namespace MediSchedule.Domain.Entities
{
    public class Doctor
    {
        public int Id { get; set; }
        public string FirstName { get; set; } = default!;
        public string LastName { get; set; } = default!;
        public string Specialization { get; set; } = default!;

        // Stawka godzinowa / za wizytę
        public decimal BaseRate { get; set; }

        // Soft delete - zamiast usuwać z bazy, będziemy deaktywować
        public bool IsActive { get; set; } = true;
    }
}