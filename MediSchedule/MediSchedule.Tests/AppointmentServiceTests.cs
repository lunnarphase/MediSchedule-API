using MediSchedule.Application.DTOs;
using MediSchedule.Application.Interfaces;
using MediSchedule.Application.Services;
using MediSchedule.Domain.Entities;
using MediSchedule.Domain.Enums;
using Moq;

namespace MediSchedule.Tests
{
    public class AppointmentServiceTests
    {
        private readonly Mock<IAppointmentRepository> _mockAppointmentRepo;
        private readonly Mock<IDoctorRepository> _mockDoctorRepo;
        private readonly Mock<IScheduleRepository> _mockScheduleRepo;
        private readonly AppointmentService _service;

        public AppointmentServiceTests()
        {
            _mockAppointmentRepo = new Mock<IAppointmentRepository>();
            _mockDoctorRepo = new Mock<IDoctorRepository>();
            _mockScheduleRepo = new Mock<IScheduleRepository>();

            _service = new AppointmentService(
                _mockAppointmentRepo.Object,
                _mockDoctorRepo.Object,
                _mockScheduleRepo.Object
            );
        }

        // Test 1: Poprawna kalkulacja ceny
        [Fact]
        public async Task ScheduleAppointmentAsync_ShouldCalculatePriceCorrectly()
        {
            // Test sprawdzajacy czy metoda ScheduleAppointmentAsync poprawnie oblicza cene wizyty na podstawie stawki bazowej lekarza i czasu trwania wizyty
            var dto = new CreateAppointmentDto { DoctorId = 1, PatientId = 1, StartTime = new DateTime(2024, 6, 10, 10, 0, 0), DurationMinutes = 30 };
            var doctor = new Doctor { Id = 1, BaseRate = 200, IsActive = true };
            var schedule = new Schedule { StartTime = new TimeSpan(8, 0, 0), EndTime = new TimeSpan(16, 0, 0), DayOfWeek = DayOfWeek.Monday };

            _mockDoctorRepo.Setup(r => r.GetByIdAsync(1)).ReturnsAsync(doctor);
            _mockAppointmentRepo.Setup(r => r.HasOverlapAsync(1, It.IsAny<DateTime>(), It.IsAny<DateTime>())).ReturnsAsync(false);
            _mockScheduleRepo.Setup(r => r.GetDoctorScheduleForDayAsync(1, DayOfWeek.Monday)).ReturnsAsync(schedule);

            // Act
            var result = await _service.ScheduleAppointmentAsync(dto);

            // Assert
            Assert.Equal(100, result.Price);
        }

        // Test 2: B³¹d kolizji terminów
        [Fact]
        public async Task ScheduleAppointmentAsync_ShouldThrowException_WhenOverlapExists()
        {
            var dto = new CreateAppointmentDto { DoctorId = 1, StartTime = new DateTime(2024, 6, 10, 10, 0, 0), DurationMinutes = 30 };
            var doctor = new Doctor { Id = 1, BaseRate = 100, IsActive = true };
            var schedule = new Schedule { StartTime = new TimeSpan(8, 0, 0), EndTime = new TimeSpan(16, 0, 0), DayOfWeek = DayOfWeek.Monday };

            _mockDoctorRepo.Setup(r => r.GetByIdAsync(1)).ReturnsAsync(doctor);
            _mockScheduleRepo.Setup(r => r.GetDoctorScheduleForDayAsync(1, DayOfWeek.Monday)).ReturnsAsync(schedule);
            _mockAppointmentRepo.Setup(r => r.HasOverlapAsync(1, It.IsAny<DateTime>(), It.IsAny<DateTime>())).ReturnsAsync(true);

            await Assert.ThrowsAsync<InvalidOperationException>(() => _service.ScheduleAppointmentAsync(dto));
        }

        // Test 3: B³¹d braku lekarza
        [Fact]
        public async Task ScheduleAppointmentAsync_ShouldThrowException_WhenDoctorNotFound()
        {
            var dto = new CreateAppointmentDto { DoctorId = 999 };
            _mockDoctorRepo.Setup(r => r.GetByIdAsync(999)).ReturnsAsync((Doctor)null);

            await Assert.ThrowsAsync<Exception>(() => _service.ScheduleAppointmentAsync(dto));
        }

        // Test 4: Ustawienie statusu na Scheduled
        [Fact]
        public async Task ScheduleAppointmentAsync_ShouldSetStatusToScheduled()
        {
            var dto = new CreateAppointmentDto { DoctorId = 1, StartTime = new DateTime(2024, 6, 10, 10, 0, 0), DurationMinutes = 30 };
            var doctor = new Doctor { Id = 1, BaseRate = 100, IsActive = true };
            var schedule = new Schedule { StartTime = new TimeSpan(8, 0, 0), EndTime = new TimeSpan(16, 0, 0), DayOfWeek = DayOfWeek.Monday };

            _mockDoctorRepo.Setup(r => r.GetByIdAsync(1)).ReturnsAsync(doctor);
            _mockAppointmentRepo.Setup(r => r.HasOverlapAsync(1, It.IsAny<DateTime>(), It.IsAny<DateTime>())).ReturnsAsync(false);
            _mockScheduleRepo.Setup(r => r.GetDoctorScheduleForDayAsync(1, DayOfWeek.Monday)).ReturnsAsync(schedule);

            var result = await _service.ScheduleAppointmentAsync(dto);

            Assert.Equal(AppointmentStatus.Scheduled, result.Status);
        }

        // Test 5: Wywo³anie metody AddAsync w repozytorium
        [Fact]
        public async Task ScheduleAppointmentAsync_ShouldCallRepositoryAdd_Once()
        {
            var dto = new CreateAppointmentDto { DoctorId = 1, StartTime = new DateTime(2024, 6, 10, 10, 0, 0), DurationMinutes = 30 };
            var doctor = new Doctor { Id = 1, BaseRate = 100, IsActive = true };
            var schedule = new Schedule { StartTime = new TimeSpan(8, 0, 0), EndTime = new TimeSpan(16, 0, 0), DayOfWeek = DayOfWeek.Monday };

            _mockDoctorRepo.Setup(r => r.GetByIdAsync(1)).ReturnsAsync(doctor);
            _mockAppointmentRepo.Setup(r => r.HasOverlapAsync(1, It.IsAny<DateTime>(), It.IsAny<DateTime>())).ReturnsAsync(false);
            _mockScheduleRepo.Setup(r => r.GetDoctorScheduleForDayAsync(1, DayOfWeek.Monday)).ReturnsAsync(schedule);

            await _service.ScheduleAppointmentAsync(dto);

            _mockAppointmentRepo.Verify(r => r.AddAsync(It.IsAny<Appointment>()), Times.Once);
        }
    }
}