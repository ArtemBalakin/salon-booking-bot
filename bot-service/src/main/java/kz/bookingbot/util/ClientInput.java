package kz.bookingbot.util;

/**
 * Нормализация и валидация данных, которые клиент вводит в Mini App.
 * Бросает {@link IllegalArgumentException} с текстом, пригодным для показа пользователю.
 */
public final class ClientInput {

    private ClientInput() {
    }

    public static String normalizeName(String raw) {
        String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (name.length() < 2 || name.length() > 60) {
            throw new IllegalArgumentException("Укажите имя (от 2 до 60 символов)");
        }
        return name;
    }

    /** Приводит телефон к виду +7XXXXXXXXXX (для казахстанских/российских номеров). */
    public static String normalizePhone(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        if (digits.length() == 10) {
            digits = "7" + digits;
        } else if (digits.length() == 11 && digits.startsWith("8")) {
            digits = "7" + digits.substring(1);
        }
        if (digits.length() < 11 || digits.length() > 15) {
            throw new IllegalArgumentException("Укажите телефон полностью, например +7 701 123 45 67");
        }
        return "+" + digits;
    }
}
