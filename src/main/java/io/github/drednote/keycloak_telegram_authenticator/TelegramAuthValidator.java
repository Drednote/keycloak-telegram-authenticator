package io.github.drednote.keycloak_telegram_authenticator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.apache.hc.core5.net.URIBuilder;
import org.jboss.logging.Logger;

public class TelegramAuthValidator {

    private static final Charset UTF_8 = StandardCharsets.UTF_8;
    private static final byte[] WEB_APP_DATA = "WebAppData".getBytes(UTF_8);
    private static final Logger log = Logger.getLogger(TelegramAuthValidator.class);

    private static final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private TelegramAuthValidator() {
    }

    public static AuthResponse validateData(AuthParams params) {
        // Разбираем строку initData в Map
        Map<String, String> dataMap = parseInitData(params.initData);
        String hash = dataMap.remove("hash");

        String dataCheck = getDataCheck(dataMap);

        // Генерируем секретный ключ
        byte[] secretKey = hmacSHA256(params.botToken, WEB_APP_DATA).asBytes();

        // Вычисляем HMAC для строки проверки данных
        String calculatedHash = hmacSHA256(dataCheck, secretKey).toString();

        boolean hashValid = false;
        boolean dateValid = false;

        // Сравниваем хэши
        if (hash.equals(calculatedHash)) {
            hashValid = true;
            dateValid = checkAuthDate(dataMap, params);
        }

        dataMap.put("hash", hash);
        return new AuthResponse(hashValid, dateValid, dataMap);
    }

    private static boolean checkAuthDate(Map<String, String> dataMap, AuthParams params) {
        if (!params.checkDate) {
            return true;
        }
        // Проверка поля auth_date на актуальность
        long authDate = Long.parseLong(dataMap.get("auth_date"));
        long currentTime = Instant.now().getEpochSecond();

        return currentTime - authDate <= params.authDelta;
    }

    private static String getDataCheck(Map<String, String> dataMap) {
        // Сортируем ключи и формируем строку проверки данных
        TreeMap<String, String> sortedMap = new TreeMap<>(dataMap);
        StringBuilder dataCheck = new StringBuilder();

        for (Map.Entry<String, String> entry : sortedMap.entrySet()) {
            dataCheck.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }

        dataCheck.delete(dataCheck.length() - 1, dataCheck.length());

        return dataCheck.toString();
    }

    private static Map<String, String> parseInitData(String initData) {
        Map<String, String> params = new HashMap<>();
        try {
            URIBuilder builder = new URIBuilder(initData);
            String path = String.join("", builder.getPathSegments());
            String[] pairs = path.split("&");

            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                }
            }
            return params;
        } catch (URISyntaxException e) {
            try {
                return objectMapper.readValue(initData, new TypeReference<>() {});
            } catch (JsonProcessingException ex) {
                log.error("Failed to parse init data", ex);
            }
        }
        return params;
    }

    private static HashCode hmacSHA256(String data, byte[] key) {
        return Hashing.hmacSha256(key).hashString(data, UTF_8);
    }

    /**
     * @param authDelta in seconds
     */
    public record AuthParams(
        String initData, String botToken, boolean checkDate, int authDelta
    ) {}

    public record AuthResponse(
        boolean hashValid, boolean dateValid, Map<String, String> data, TelegramUser user
    ) {

        public AuthResponse(boolean hashValid, boolean dateValid, Map<String, String> data) {
            this(hashValid, dateValid, data, parseUser(data));
        }

        private static TelegramUser parseUser(Map<String, String> data) {
            if (data == null || data.get("user") == null) {
                throw new IllegalArgumentException("data is null");
            }
            String user = data.get("user");
            try {
                TelegramUser appUser = objectMapper.readValue(user, TelegramUser.class);
                if (appUser.getId() == null) {
                    throw new IllegalArgumentException("id is null");
                }
                if (appUser.getUserName() == null || appUser.getUserName().isBlank()) {
                    appUser.setUserName(UUID.randomUUID().toString());
                }
                return appUser;
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Failed to parse user", e);
            }
        }
    }
}
