package kz.bookingbot.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientInputTest {

    @Test
    void normalizesPhoneVariants() {
        assertEquals("+77011234567", ClientInput.normalizePhone("+7 (701) 123-45-67"));
        assertEquals("+77011234567", ClientInput.normalizePhone("87011234567"));
        assertEquals("+77011234567", ClientInput.normalizePhone("7011234567"));
    }

    @Test
    void rejectsShortPhone() {
        assertThrows(IllegalArgumentException.class, () -> ClientInput.normalizePhone("+7 (701) 12"));
        assertThrows(IllegalArgumentException.class, () -> ClientInput.normalizePhone(null));
    }

    @Test
    void normalizesName() {
        assertEquals("Алия Нурова", ClientInput.normalizeName("  Алия   Нурова "));
        assertThrows(IllegalArgumentException.class, () -> ClientInput.normalizeName(" А "));
        assertThrows(IllegalArgumentException.class, () -> ClientInput.normalizeName(null));
    }
}
