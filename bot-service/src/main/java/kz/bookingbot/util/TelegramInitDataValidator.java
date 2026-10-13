package kz.bookingbot.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Validates Telegram Mini App initData per
 * https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app
 */
public final class TelegramInitDataValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long MAX_AGE_SECONDS = 24 * 60 * 60;
    private static final long MAX_FUTURE_SECONDS = 60;

    private TelegramInitDataValidator() {
    }

    public static boolean isValid(String initData, String botToken) {
        if (initData == null || initData.isBlank() || botToken == null || botToken.isBlank()) {
            return false;
        }
        try {
            Map<String, String> fields = parse(initData);
            String receivedHash = fields.remove("hash");
            if (receivedHash == null) {
                return false;
            }
            // Even correctly signed initData must not be reusable indefinitely.
            long authDate = Long.parseLong(fields.get("auth_date"));
            long now = Instant.now().getEpochSecond();
            if (authDate <= 0 || authDate > now + MAX_FUTURE_SECONDS
                    || now - authDate > MAX_AGE_SECONDS) {
                return false;
            }
            String dataCheckString = buildDataCheckString(fields);

            byte[] secretKey = hmac(botToken.getBytes(StandardCharsets.UTF_8), "WebAppData".getBytes(StandardCharsets.UTF_8));
            byte[] computedHash = hmac(dataCheckString.getBytes(StandardCharsets.UTF_8), secretKey);

            // Avoid variable-time comparisons of authentication data.
            return MessageDigest.isEqual(computedHash, HexFormat.of().parseHex(receivedHash));
        } catch (Exception e) {
            return false;
        }
    }

    public static Map<String, String> parse(String initData) {
        Map<String, String> result = new TreeMap<>();
        for (String pair : initData.split("&")) {
            int idx = pair.indexOf('=');
            if (idx < 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            if (result.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate initData field: " + key);
            }
        }
        return result;
    }

    private static String buildDataCheckString(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    private static byte[] hmac(byte[] message, byte[] key) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(key, HMAC_SHA256));
        return mac.doFinal(message);
    }

}
