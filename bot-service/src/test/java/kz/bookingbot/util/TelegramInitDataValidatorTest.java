package kz.bookingbot.util;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class TelegramInitDataValidatorTest {
    private static final String BOT_TOKEN = "123456:TEST-TOKEN";

    @Test
    void acceptsProperlySignedRecentInitData() throws Exception {
        String data = signed(Instant.now().getEpochSecond());
        assertTrue(TelegramInitDataValidator.isValid(data, BOT_TOKEN));
    }

    @Test
    void rejectsStaleInitDataEvenWhenSignatureIsValid() throws Exception {
        assertFalse(TelegramInitDataValidator.isValid(signed(Instant.now().minusSeconds(86401).getEpochSecond()), BOT_TOKEN));
    }

    @Test
    void rejectsFutureAndTamperedInitData() throws Exception {
        assertFalse(TelegramInitDataValidator.isValid(signed(Instant.now().plusSeconds(120).getEpochSecond()), BOT_TOKEN));
        assertFalse(TelegramInitDataValidator.isValid(signed(Instant.now().getEpochSecond()).replace("John", "Admin"), BOT_TOKEN));
    }

    @Test
    void rejectsDuplicateFields() throws Exception {
        String valid = signed(Instant.now().getEpochSecond());
        assertFalse(TelegramInitDataValidator.isValid(valid + "&user=%7B%7D", BOT_TOKEN));
    }

    private static String signed(long authDate) throws Exception {
        Map<String, String> fields = new TreeMap<>();
        fields.put("auth_date", Long.toString(authDate));
        fields.put("query_id", "test_query");
        fields.put("user", "{\"id\":42,\"first_name\":\"John\"}");
        String dataCheckString = String.join("\n", fields.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).toList());
        byte[] secret = hmac(BOT_TOKEN.getBytes(StandardCharsets.UTF_8), "WebAppData".getBytes(StandardCharsets.UTF_8));
        fields.put("hash", HexFormat.of().formatHex(hmac(dataCheckString.getBytes(StandardCharsets.UTF_8), secret)));
        return String.join("&", fields.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).toList());
    }

    private static byte[] hmac(byte[] message, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(message);
    }
}
