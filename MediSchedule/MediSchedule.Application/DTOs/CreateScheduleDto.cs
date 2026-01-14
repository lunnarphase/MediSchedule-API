using System.ComponentModel.DataAnnotations;

namespace MediSchedule.Application.DTOs
{
    public class CreateScheduleDto
    {
        [Required]
        public int DoctorId { get; set; }

        [Range(0, 6)]
        public DayOfWeek DayOfWeek { get; set; } // 0 = Niedziela, 1 = Poniedziałek...

        public TimeSpan StartTime { get; set; }  // Format "08:00:00"
        public TimeSpan EndTime { get; set; }
    }
}