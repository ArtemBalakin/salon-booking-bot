package kz.bookingbot.service;

import kz.bookingbot.entity.Booking;
import kz.bookingbot.entity.BookingStatus;
import kz.bookingbot.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Смена статусов записи (подтверждение, отклонение, отмена) и выборки для бота.
 * Все изменения идут под блокировкой строки записи, чтобы двойной клик по кнопке
 * или одновременные действия клиента и админа не ломали состояние слота.
 */
@Service
public class BookingLifecycleService {

    /** Операция невозможна в текущем статусе записи; текст можно показывать пользователю. */
    public static class BookingStateException extends RuntimeException {
        public BookingStateException(String message) {
            super(message);
        }
    }

    private final BookingRepository bookingRepository;

    public BookingLifecycleService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public Booking confirm(Long bookingId) {
        Booking booking = lock(bookingId);
        if (!BookingStatus.PENDING.equals(booking.getStatus())) {
            throw new BookingStateException("Запись #" + bookingId + " уже обработана: " + label(booking.getStatus()));
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.skipPassedReminders(LocalDateTime.now());
        return booking;
    }

    @Transactional
    public Booking reject(Long bookingId) {
        Booking booking = lock(bookingId);
        if (!BookingStatus.PENDING.equals(booking.getStatus())) {
            throw new BookingStateException("Запись #" + bookingId + " уже обработана: " + label(booking.getStatus()));
        }
        booking.setStatus(BookingStatus.REJECTED);
        booking.getSlot().setBooked(false);
        return booking;
    }

    @Transactional
    public Booking cancelByClient(Long bookingId, Long telegramUserId) {
        Booking booking = lock(bookingId);
        if (!telegramUserId.equals(booking.getTelegramUserId())) {
            throw new BookingStateException("Это не ваша запись");
        }
        if (!BookingStatus.ACTIVE.contains(booking.getStatus())) {
            throw new BookingStateException("Запись уже отменена");
        }
        if (!booking.getSlot().getStartTime().isAfter(LocalDateTime.now())) {
            throw new BookingStateException("Запись уже прошла, отменить её нельзя");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        booking.getSlot().setBooked(false);
        return booking;
    }

    @Transactional(readOnly = true)
    public List<Booking> upcomingForUser(Long telegramUserId) {
        return bookingRepository.findUpcomingForUser(telegramUserId, BookingStatus.ACTIVE, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<Booking> activeForDay(LocalDate day) {
        return bookingRepository.findInRange(day.atStartOfDay(), day.plusDays(1).atStartOfDay(), BookingStatus.ACTIVE);
    }

    private Booking lock(Long bookingId) {
        return bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingStateException("Запись #" + bookingId + " не найдена"));
    }

    private static String label(String status) {
        return switch (status) {
            case BookingStatus.CONFIRMED -> "подтверждена";
            case BookingStatus.CANCELLED -> "отменена клиентом";
            case BookingStatus.REJECTED -> "отклонена";
            default -> "ожидает подтверждения";
        };
    }
}
