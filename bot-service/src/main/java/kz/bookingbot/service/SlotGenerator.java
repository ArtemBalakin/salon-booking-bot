package kz.bookingbot.service;

import kz.bookingbot.entity.Master;
import kz.bookingbot.entity.Slot;
import kz.bookingbot.repository.MasterRepository;
import kz.bookingbot.repository.SlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Поддерживает расписание «скользящим окном»: каждый день добивает свободные
 * часовые слоты у каждого мастера на N дней вперёд, чтобы демо/салон не оставались без окошек.
 */
@Service
public class SlotGenerator {

    private static final Logger log = LoggerFactory.getLogger(SlotGenerator.class);

    private final MasterRepository masterRepository;
    private final SlotRepository slotRepository;

    @Value("${salon.work-start-hour:10}")
    private int workStartHour;

    @Value("${salon.work-end-hour:19}")
    private int workEndHour;

    @Value("${salon.slots-ahead-days:14}")
    private int aheadDays;

    public SlotGenerator(MasterRepository masterRepository, SlotRepository slotRepository) {
        this.masterRepository = masterRepository;
        this.slotRepository = slotRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 10 0 * * *")
    @Transactional
    public void ensureSlots() {
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to = today.plusDays(aheadDays).atStartOfDay();

        List<Slot> toCreate = new ArrayList<>();
        for (Master master : masterRepository.findAll()) {
            Set<LocalDateTime> existing = new HashSet<>(slotRepository.findStartTimes(master.getId(), from, to));
            for (int day = 0; day < aheadDays; day++) {
                LocalDate date = today.plusDays(day);
                for (int hour = workStartHour; hour < workEndHour; hour++) {
                    LocalDateTime start = date.atTime(hour, 0);
                    if (existing.contains(start)) {
                        continue;
                    }
                    Slot slot = new Slot();
                    slot.setMaster(master);
                    slot.setStartTime(start);
                    slot.setEndTime(start.plusHours(1));
                    slot.setBooked(false);
                    toCreate.add(slot);
                }
            }
        }
        if (!toCreate.isEmpty()) {
            slotRepository.saveAll(toCreate);
            log.info("Generated {} new slots up to {}", toCreate.size(), to.toLocalDate());
        }
    }
}
