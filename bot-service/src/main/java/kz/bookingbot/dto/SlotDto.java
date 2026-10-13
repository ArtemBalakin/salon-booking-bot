package kz.bookingbot.dto;

import java.time.LocalDateTime;

public record SlotDto(Long id, LocalDateTime startTime, LocalDateTime endTime) {
}
