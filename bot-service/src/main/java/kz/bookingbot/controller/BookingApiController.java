package kz.bookingbot.controller;

import kz.bookingbot.dto.BookingResponse;
import kz.bookingbot.dto.CreateBookingRequest;
import kz.bookingbot.dto.MasterDto;
import kz.bookingbot.dto.ServiceDto;
import kz.bookingbot.dto.SlotDto;
import kz.bookingbot.service.BookingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
public class BookingApiController {

    private final BookingService bookingService;

    @Value("${salon.name:Салон красоты}")
    private String salonName;

    @Value("${salon.address:}")
    private String salonAddress;

    public BookingApiController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** Only public, display-safe settings; credentials are never exposed to the browser. */
    @GetMapping("/api/public-config")
    public Map<String, String> publicConfig() {
        return Map.of("name", salonName, "address", salonAddress);
    }

    @GetMapping("/api/services")
    public List<ServiceDto> services() {
        return bookingService.listServices();
    }

    @GetMapping("/api/masters")
    public List<MasterDto> masters(@RequestParam(required = false) Long serviceId) {
        return bookingService.listMasters(serviceId);
    }

    @GetMapping("/api/slots")
    public List<SlotDto> slots(@RequestParam Long masterId,
                                @RequestParam String date) {
        return bookingService.listFreeSlots(masterId, LocalDate.parse(date));
    }

    @PostMapping("/api/bookings")
    public ResponseEntity<BookingResponse> createBooking(@RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(request));
    }
}
