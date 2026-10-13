package kz.bookingbot.repository;

import kz.bookingbot.entity.Master;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MasterRepository extends JpaRepository<Master, Long> {

    @Query("SELECT m FROM Master m JOIN m.services s WHERE s.id = :serviceId ORDER BY m.name")
    List<Master> findByServiceId(Long serviceId);

    boolean existsByIdAndServicesId(Long id, Long serviceId);
}
