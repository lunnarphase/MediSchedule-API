using MediSchedule.Application.DTOs;
using MediSchedule.Application.Interfaces;
using MediSchedule.Application.Services;
using MediSchedule.Domain.Entities;
using Moq;
using Xunit;

namespace MediSchedule.Tests
{
    public class AppointmentServiceTests
    {
        private readonly Mock<IAppointmentRepository> _appointmentRepoMock;
        private readonly Mock<IDoctorRepository> _doctorRepoMock;
        private readonly AppointmentService _service;

        public AppointmentServiceTests()
        {
            _appointmentRepoMock = new Mock<IAppointmentRepository>();
            _doctorRepoMock = new Mock<IDoctorRepository>();
            _service = new AppointmentService(_appointmentRepoMock.Object, _doctorRepoMock.Object);
        }

        [Fact]
        public async Task Schedule_ShouldCalculatePriceCorrectly()
        {
            // Przygotowanie
            var doctor = new Doctor { Id = 1, BaseRate = 200, IsActive = true };
            _doctorRepoMock.Setup(r => r.GetByIdAsync(1)).ReturnsAsync(doctor);

            // Symulujemy, ¿e nie ma kolizji (zwraca false)
            _appointmentRepoMock.Setup(r => r.HasOverlapAsync(It.IsAny<int>(), It.IsAny<DateTime>(), It.IsAny<DateTime>()))
                .ReturnsAsync(false);

            var dto = new CreateAppointmentDto
            {
                DoctorId = 1,
                StartTime = DateTime.Now,
                DurationMinutes = 90 // 1.5 godziny
            };

            // Wykonanie
            var result = await _service.ScheduleAppointmentAsync(dto);

            // Sprawdzenie
            // 200 z³/h * 1.5h = 300 z³
            Assert.Equal(300m, result.Price);
        }

        [Fact]
        public async Task Schedule_ShouldThrowException_WhenOverlapExists()
        {
            // Przygotowanie
            var doctor = new Doctor { Id = 1, BaseRate = 100, IsActive = true };
            _doctorRepoMock.Setup(r => r.GetByIdAsync(1)).ReturnsAsync(doctor);

            // Symulujemy, ¿e jest kolizja (zwraca true)
            _appointmentRepoMock.Setup(r => r.HasOverlapAsync(1, It.IsAny<DateTime>(), It.IsAny<DateTime>()))
                .ReturnsAsync(true);

            var dto = new CreateAppointmentDto { DoctorId = 1, StartTime = DateTime.Now, DurationMinutes = 60 };

            // Sprawdzenie i wykonanie
            await Assert.ThrowsAsync<InvalidOperationException>(() => _service.ScheduleAppointmentAsync(dto));
        }

        [Fact]
        public async Task Schedule_ShouldThrowException_WhenDoctorNotFound()
        {
            // Przygotowanie
            // Symulujemy, ¿e repozytorium zwróci³o null (brak lekarza)
            _doctorRepoMock.Setup(r => r.GetByIdAsync(999)).ReturnsAsync((Doctor?)null);

            var dto = new CreateAppointmentDto { DoctorId = 999 };

            // Sprawdzenie i wykonanie
            await Assert.ThrowsAsync<Exception>(() => _service.ScheduleAppointmentAsync(dto));
        }

        [Fact]
        public async Task Schedule_ShouldSetStatusToScheduled()
        {
            // Przygotowanie
            var doctor = new Doctor { Id = 1, BaseRate = 100, IsActive = true };
            _doctorRepoMock.Setup(r => r.GetByIdAsync(1)).ReturnsAsync(doctor);
            _appointmentRepoMock.Setup(r => r.HasOverlapAsync(It.IsAny<int>(), It.IsAny<DateTime>(), It.IsAny<DateTime>()))
                .ReturnsAsync(false);

            var dto = new CreateAppointmentDto { DoctorId = 1, DurationMinutes = 60 };

            // Wykonanie
            var result = await _service.ScheduleAppointmentAsync(dto);

            // Sprawdzenie
            Assert.Equal(Domain.Enums.AppointmentStatus.Scheduled, result.Status);
        }

        [Fact]
        public async Task Schedule_ShouldCallRepositoryAdd_Once()
        {
            // Przygotowanie
            var doctor = new Doctor { Id = 1, BaseRate = 100, IsActive = true };
            _doctorRepoMock.Setup(r => r.GetByIdAsync(1)).ReturnsAsync(doctor);

            var dto = new CreateAppointmentDto { DoctorId = 1, DurationMinutes = 60 };

            // Wykonanie
            await _service.ScheduleAppointmentAsync(dto);

            // Sprawdzamy czy metoda AddAsync zosta³a wywo³ana dok³adnie raz
            _appointmentRepoMock.Verify(r => r.AddAsync(It.IsAny<Appointment>()), Times.Once);
        }
    }
}