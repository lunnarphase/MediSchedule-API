using System.ComponentModel.DataAnnotations;

namespace MediSchedule.Application.DTOs
{
    // Obiektu używany tylko przy tworzeniu - metoda POST
    // Nie ma pola "Id", ponieważ to baza je generuje
    public class CreateDoctorDto
    {
        [Required(ErrorMessage = "Imię jest wymagane")]
        [MaxLength(50)]
        public string FirstName { get; set; } = default!;

        [Required(ErrorMessage = "Nazwisko jest wymagane")]
        [MaxLength(50)]
        public string LastName { get; set; } = default!;

        [Required]
        public string Specialization { get; set; } = default!;

        [Range(0, 10000, ErrorMessage = "Stawka musi być dodatnia")]
        public decimal BaseRate { get; set; }
    }
}