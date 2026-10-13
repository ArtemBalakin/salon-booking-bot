package kz.bookingbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.bookingbot.dto.BookingResponse;
import kz.bookingbot.dto.CreateBookingRequest;
import kz.bookingbot.dto.MasterDto;
import kz.bookingbot.dto.ServiceDto;
import kz.bookingbot.dto.SlotDto;
import kz.bookingbot.entity.Booking;
import kz.bookingbot.entity.BookingStatus;
import kz.bookingbot.entity.Master;
import kz.bookingbot.entity.ServiceEntity;
import kz.bookingbot.entity.Slot;
import kz.bookingbot.repository.BookingRepository;
import kz.bookingbot.repository.MasterRepository;
import kz.bookingbot.repository.ServiceRepository;
import kz.bookingbot.repository.SlotRepository;
import kz.bookingbot.util.ClientInput;
import kz.bookingbot.util.TelegramInitDataValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class BookingService {

    private final ServiceRepository serviceRepository;
    private final MasterRepository masterRepository;
    private final SlotRepository slotRepository;
    private final BookingRepository bookingRepository;
    private final ObjectProvider<BookingNotifier> notifiers;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${bot.token}")
    private String botToken;

    @Value("${bot.admin-chat-id:}")
    private String adminChatId;

    @Value("${bot.manual-confirmation:true}")
    private boolean manualConfirmation;

    public BookingService(ServiceRepository serviceRepository,
                           MasterRepository masterRepository,
                           SlotRepository slotRepository,
                           BookingRepository bookingRepository,
                           ObjectProvider<BookingNotifier> notifiers) {
        this.serviceRepository = serviceRepository;
        this.masterRepository = masterRepository;
        this.slotRepository = slotRepository;
        this.bookingRepository = bookingRepository;
        this.notifiers = notifiers;
    }

    public List<ServiceDto> listServices() {
        return serviceRepository.findAll().stream()
                .map(s -> new ServiceDto(s.getId(), s.getName(), s.getDurationMinutes(), s.getPriceTenge()))
                .toList();
    }

    public List<MasterDto> listMasters(Long serviceId) {
        List<Master> masters = serviceId == null
                ? masterRepository.findAll()
                : masterRepository.findByServiceId(serviceId);
        return masters.stream()
                .map(m -> new MasterDto(m.getId(), m.getName()))
                .toList();
    }

    public List<SlotDto> listFreeSlots(Long masterId, LocalDate date) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = date.atStartOfDay();
        if (from.isBefore(now)) {
            from = now;
        }
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        return slotRepository.findFreeSlots(masterId, from, to).stream()
                .map(s -> new SlotDto(s.getId(), s.getStartTime(), s.getEndTime()))
                .toList();
    }

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
        if (request == null || request.serviceId() == null || request.serviceId() <= 0
                || request.slotId() == null || request.slotId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите услугу и время записи");
        }
        if (!TelegramInitDataValidator.isValid(request.initData(), botToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Откройте приложение через бота в Telegram");
        }
        TelegramUser user = extractUser(request.initData());

        String clientName;
        String clientPhone;
        try {
            clientName = ClientInput.normalizeName(request.clientName());
            clientPhone = ClientInput.normalizePhone(request.clientPhone());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        Slot slot = slotRepository.findByIdForUpdate(request.slotId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Время не найдено"));
        if (slot.isBooked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Это время уже занято, выберите другое");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!slot.getStartTime().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Это время уже прошло, выберите другое");
        }
        ServiceEntity service = serviceRepository.findById(request.serviceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Услуга не найдена"));
        if (!masterRepository.existsByIdAndServicesId(slot.getMaster().getId(), service.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Этот мастер не выполняет выбранную услугу");
        }

        slot.setBooked(true);
        slotRepository.save(slot);

        Booking booking = new Booking();
        booking.setSlot(slot);
        booking.setService(service);
        booking.setTelegramUserId(user.id());
        booking.setTelegramUsername(user.username());
        booking.setClientName(clientName);
        booking.setClientPhone(clientPhone);
        if (requiresAdminConfirmation()) {
            booking.setStatus(BookingStatus.PENDING);
        } else {
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.skipPassedReminders(now);
        }
        bookingRepository.save(booking);

        // Уведомляем только после успешного коммита, чтобы не слать сообщения о записях,
        // которые в итоге не сохранились.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifiers.ifAvailable(n -> n.notifyNewBooking(booking));
            }
        });

        return new BookingResponse(booking.getId(), service.getName(), slot.getMaster().getName(),
                slot.getStartTime(), booking.getStatus());
    }

    /** Подтверждение админом возможно только если задан чат администратора. */
    private boolean requiresAdminConfirmation() {
        return manualConfirmation && adminChatId != null && !adminChatId.isBlank();
    }

    private TelegramUser extractUser(String initData) {
        try {
            Map<String, String> fields = TelegramInitDataValidator.parse(initData);
            String userJson = fields.get("user");
            JsonNode node = objectMapper.readTree(userJson);
            String username = node.hasNonNull("username") ? node.get("username").asText() : null;
            return new TelegramUser(node.get("id").asLong(), username);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось определить пользователя Telegram");
        }
    }

    private record TelegramUser(Long id, String username) {
    }
}
