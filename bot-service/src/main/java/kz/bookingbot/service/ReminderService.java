package kz.bookingbot.service;

import kz.bookingbot.entity.Booking;
import kz.bookingbot.repository.BookingRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Раз в минуту рассылает клиентам напоминания о визите за 24 часа и за 2 часа.
 */
@Service
public class ReminderService {

    private final BookingRepository bookingRepository;
    private final ObjectProvider<BookingNotifier> notifiers;

    public ReminderService(BookingRepository bookingRepository, ObjectProvider<BookingNotifier> notifiers) {
        this.bookingRepository = bookingRepository;
        this.notifiers = notifiers;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void sendDueReminders() {
        BookingNotifier notifier = notifiers.getIfAvailable();
        if (notifier == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        for (Booking booking : bookingRepository.findDueFor24hReminder(now, now.plusHours(24))) {
            // Если до визита уже < 2 часов, отдельное «за сутки» не шлём — уйдёт только «за 2 часа»
            if (booking.getSlot().getStartTime().isAfter(now.plusHours(2))) {
                notifier.sendReminder(booking, 24);
            }
            booking.setReminder24hSent(true);
        }
        for (Booking booking : bookingRepository.findDueFor2hReminder(now, now.plusHours(2))) {
            notifier.sendReminder(booking, 2);
            booking.setReminder2hSent(true);
        }
    }
}
