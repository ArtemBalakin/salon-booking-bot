package kz.bookingbot.entity;

import java.util.List;

public final class BookingStatus {

    /** Создана клиентом, ждёт подтверждения администратора. */
    public static final String PENDING = "PENDING";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String CANCELLED = "CANCELLED";
    public static final String REJECTED = "REJECTED";

    /** Статусы, при которых запись занимает слот. */
    public static final List<String> ACTIVE = List.of(PENDING, CONFIRMED);

    private BookingStatus() {
    }
}
