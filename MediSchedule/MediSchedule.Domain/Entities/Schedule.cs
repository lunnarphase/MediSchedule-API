namespace MediSchedule.Domain.Entities
{
    public class Schedule
    {
        public int Id { get; set; }
        public int DoctorId { get; set; }
        public DayOfWeek DayOfWeek { get; set; } // 0 = Niedziela, 1 = Poniedziałek, ..., 6 = Sobota
        public TimeSpan StartTime { get; set; }
        public TimeSpan EndTime { get; set; }
        public Doctor Doctor { get; set; } = default!;
    }
}