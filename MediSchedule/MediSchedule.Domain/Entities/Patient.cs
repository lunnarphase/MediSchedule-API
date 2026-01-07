namespace MediSchedule.Domain.Entities
{
    public class Patient
    {
        public int Id { get; set; }
        public string FirstName { get; set; } = default!;
        public string LastName { get; set; } = default!;
        public string Email { get; set; } = default!;

        // PESEL jako string, ponieważ może zaczynać się od zera oraz nie wykonujemy na nim działań matematycznych
        public string Pesel { get; set; } = default!;

        public bool IsActive { get; set; } = true;
    }
}