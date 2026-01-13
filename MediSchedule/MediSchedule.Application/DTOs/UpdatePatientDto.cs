using System.ComponentModel.DataAnnotations;

namespace MediSchedule.Application.DTOs
{
    public class UpdatePatientDto
    {
        [Required]
        [MaxLength(50)]
        public string FirstName { get; set; } = default!;

        [Required]
        [MaxLength(50)]
        public string LastName { get; set; } = default!;

        [Required]
        [EmailAddress]
        public string Email { get; set; } = default!;

        [Required]
        [StringLength(11, MinimumLength = 11, ErrorMessage = "PESEL musi mieć 11 znaków")]
        public string Pesel { get; set; } = default!;
    }
}