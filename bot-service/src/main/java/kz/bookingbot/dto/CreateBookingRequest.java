package kz.bookingbot.dto;

public record CreateBookingRequest(
        String initData,
        Long serviceId,
        Long slotId,
        String clientName,
        String clientPhone
) {
}
