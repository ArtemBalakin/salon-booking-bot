package kz.bookingbot.dto;

import java.time.LocalDateTime;

public record BookingResponse(
        Long id,
        String serviceName,
        String masterName,
        LocalDateTime startTime,
        String status
) {
}
