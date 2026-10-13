package kz.bookingbot.repository;

import jakarta.persistence.LockModeType;
import kz.bookingbot.entity.Slot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    @Query("""
            SELECT s FROM Slot s
            WHERE s.master.id = :masterId
              AND s.booked = false
              AND s.startTime >= :from
              AND s.startTime < :to
            ORDER BY s.startTime
            """)
    List<Slot> findFreeSlots(Long masterId, LocalDateTime from, LocalDateTime to);

    @Query("""
            SELECT s.startTime FROM Slot s
            WHERE s.master.id = :masterId
              AND s.startTime >= :from
              AND s.startTime < :to
            """)
    List<LocalDateTime> findStartTimes(Long masterId, LocalDateTime from, LocalDateTime to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Slot s WHERE s.id = :id")
    Optional<Slot> findByIdForUpdate(Long id);
}
