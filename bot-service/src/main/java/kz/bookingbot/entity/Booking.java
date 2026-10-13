package kz.bookingbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "booking")
@Getter
@Setter
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Слот может быть связан с несколькими записями (старые отменённые + одна активная),
    // уникальность активной записи обеспечивает частичный индекс в БД.
    @ManyToOne
    @JoinColumn(name = "slot_id", nullable = false)
    private Slot slot;

    @ManyToOne
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceEntity service;

    private Long telegramUserId;

    private String telegramUsername;

    private String clientName;

    private String clientPhone;

    private String status = BookingStatus.CONFIRMED;

    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "reminder_24h_sent")
    private boolean reminder24hSent;

    @Column(name = "reminder_2h_sent")
    private boolean reminder2hSent;

    /**
     * Если до визита уже меньше 24 ч (или 2 ч), соответствующее напоминание
     * не имеет смысла — помечаем его отправленным.
     */
    public void skipPassedReminders(LocalDateTime now) {
        LocalDateTime start = slot.getStartTime();
        if (!start.isAfter(now.plusHours(24))) {
            reminder24hSent = true;
        }
        if (!start.isAfter(now.plusHours(2))) {
            reminder2hSent = true;
        }
    }
}
