package kz.bookingbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class BookingBotApplication {

    public static void main(String[] args) {
        // Слоты хранятся как TIMESTAMP без зоны, поэтому «сейчас» должно считаться
        // в часовом поясе салона, а не контейнера (UTC).
        String zone = System.getenv("APP_TIMEZONE");
        TimeZone.setDefault(TimeZone.getTimeZone(zone == null || zone.isBlank() ? "Asia/Almaty" : zone));
        SpringApplication.run(BookingBotApplication.class, args);
    }
}
