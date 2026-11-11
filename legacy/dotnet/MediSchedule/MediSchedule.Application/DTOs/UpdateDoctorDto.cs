using System.ComponentModel.DataAnnotations;

namespace MediSchedule.Application.DTOs
{
    public class UpdateDoctorDto
    {
        [Required]
        public string FirstName { get; set; } = default!;

        [Required]
        public string LastName { get; set; } = default!;

        [Required]
        public string Specialization { get; set; } = default!;

        [Range(0, 10000)]
        public decimal BaseRate { get; set; }
    }
}