package kz.bookingbot.service;

import kz.bookingbot.entity.Booking;

/**
 * Implemented by the Telegram bot to push booking notifications to clients and
 * to the salon owner's admin chat without the service layer depending on the
 * bot client directly.
 */
public interface BookingNotifier {

    /** Новая запись: подтверждение клиенту + уведомление админу (с кнопками, если нужно подтверждение). */
    void notifyNewBooking(Booking booking);

    /** Админ подтвердил запись — сообщаем клиенту. */
    void notifyConfirmed(Booking booking);

    /** Админ отклонил запись — сообщаем клиенту. */
    void notifyRejected(Booking booking);

    /** Клиент отменил запись — сообщаем админу. */
    void notifyCancelledByClient(Booking booking);

    /** Напоминание клиенту о визите за {@code hoursBefore} часов. */
    void sendReminder(Booking booking, int hoursBefore);
}
