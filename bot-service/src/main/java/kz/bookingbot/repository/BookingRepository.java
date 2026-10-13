package kz.bookingbot.repository;

import jakarta.persistence.LockModeType;
import kz.bookingbot.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(Long id);

    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.slot s JOIN FETCH s.master JOIN FETCH b.service
            WHERE b.telegramUserId = :userId
              AND b.status IN :statuses
              AND s.startTime > :now
            ORDER BY s.startTime
            """)
    List<Booking> findUpcomingForUser(Long userId, Collection<String> statuses, LocalDateTime now);

    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.slot s JOIN FETCH s.master JOIN FETCH b.service
            WHERE s.startTime >= :from AND s.startTime < :to
              AND b.status IN :statuses
            ORDER BY s.startTime
            """)
    List<Booking> findInRange(LocalDateTime from, LocalDateTime to, Collection<String> statuses);

    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.slot s JOIN FETCH s.master JOIN FETCH b.service
            WHERE b.status = 'CONFIRMED'
              AND b.reminder24hSent = false
              AND s.startTime > :now AND s.startTime <= :limit
            ORDER BY s.startTime
            """)
    List<Booking> findDueFor24hReminder(LocalDateTime now, LocalDateTime limit);

    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.slot s JOIN FETCH s.master JOIN FETCH b.service
            WHERE b.status = 'CONFIRMED'
              AND b.reminder2hSent = false
              AND s.startTime > :now AND s.startTime <= :limit
            ORDER BY s.startTime
            """)
    List<Booking> findDueFor2hReminder(LocalDateTime now, LocalDateTime limit);
}
