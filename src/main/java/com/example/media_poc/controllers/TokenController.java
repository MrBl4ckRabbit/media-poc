package com.example.media_poc.controllers;

import com.example.media_poc.storage.VideoMetadataCache;
import com.example.media_poc.storage.VideoStorage;
import com.example.media_poc.util.RangeUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Контроллер для защищённого стриминга видео через JWT + HTTP Range.
 * <p>
 * Эндпоинты:
 * 1) POST /token/media/batch-tokens
 * • Принимает JSON-массив ключей (имён видео).
 * • Возвращает карту { key → jwt }, где jwt содержит claim "key" и срок жизни токена.
 * <p>
 * 2) HEAD /token/media/signed/{token}
 * • Проверяет подпись и срок действия токена.
 * • Отдаёт только заголовки:
 * - Accept-Ranges: bytes
 * - Content-Length: полный размер видео
 * • Позволяет клиенту узнать метаданные без передачи тела видео.
 * <p>
 * 3) GET /token/media/signed/{token}
 * • Верифицирует токен и извлекает из него ключ (claim "key").
 * • Обрабатывает HTTP Range:
 * – Если Range отсутствует или задан без конца ("bytes=X-"),
 * выдаёт первый чанк фиксированного размера (1 МБ).
 * – Иначе выдаёт запрошенный диапазон байт.
 * • Устанавливает заголовки:
 * - Accept-Ranges: bytes
 * - Content-Range: bytes start-end/total (для 206)
 * - Content-Length: длина возвращаемого диапазона
 * - Content-Type: тип медиа, исходя из расширения
 * • Статус ответа:
 * – 206 Partial Content при наличии Range-запроса
 * – 200 OK при полном GET (без заголовка Range)
 */

@RestController
@RequestMapping("/token/media")
public class TokenController {

    private static final long INITIAL_CHUNK_SIZE = 1024 * 1024L;

    private final VideoStorage storage;
    private final VideoMetadataCache metadataCache;
    private final SecretKey secretKey;

    public TokenController(VideoStorage storage,
                           VideoMetadataCache metadataCache) {
        this.storage = storage;
        this.metadataCache = metadataCache;
        this.secretKey = Jwts.SIG.HS256.key().build();
    }

    /**
     * Принимает список ключей видео и выдаёт карту { ключ → JWT }.
     *
     * @param keys список имён видео-файлов
     * @return карта, где value — сгенерированный JWT с claim "key" и TTL 10 минут
     */
    @PostMapping("/batch-tokens")
    public Map<String, String> issueTokens(@RequestBody List<String> keys) {
        return keys.stream()
                .collect(Collectors.toMap(
                        key -> key,
                        this::generateToken
                ));
    }

    /**
     * Генерирует JWT с claim "key" и сроком действия 10 минут.
     *
     * @param key имена видео-файла
     * @return компактизированный JWT
     */
    private String generateToken(String key) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("key", key)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .signWith(secretKey)
                .compact();
    }

    /**
     * HEAD-запрос по токену для получения метаданных видео.
     *
     * @param token JWT с claim "key"
     * @return статус 200 с заголовками:
     * Accept-Ranges: bytes
     * Content-Length: полный размер видео
     */
    @RequestMapping(value = "/signed/{token}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headByToken(@PathVariable String token) {
        String key = parseAndValidate(token);
        long size = metadataCache.getSize(key);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setContentLength(size);

        return ResponseEntity.ok()
                .headers(headers)
                .build();
    }

    /**
     * GET-запрос для стриминга видео по проверенному JWT + HTTP Range.
     *
     * @param token       JWT, содержащий claim "key" (имя видео-файла)
     * @param rangeHeader значение заголовка "Range" (например, "bytes=0-1023"), может быть null
     * @return StreamingResponseBody с частичным или полным содержимым видео
     * @throws IOException при ошибках чтения потока
     */
    @GetMapping("/signed/{token}")
    public ResponseEntity<StreamingResponseBody> streamByToken(
            @PathVariable String token,
            @RequestHeader(value = "Range", required = false) String rangeHeader
    ) throws IOException {

        String key = parseAndValidate(token);
        long total = metadataCache.getSize(key);
        RangeUtil.Range r = RangeUtil.parse(rangeHeader, total);


        if (rangeHeader != null && rangeHeader.endsWith("-")) {
            long end = Math.min(r.start + INITIAL_CHUNK_SIZE - 1, r.total - 1);
            r = new RangeUtil.Range(r.start, end, r.total);
        }

        InputStream is = storage.readChunk(key, r.start, (int) r.length());

        boolean hasRange = rangeHeader != null;
        HttpStatus status = hasRange
                ? HttpStatus.PARTIAL_CONTENT
                : HttpStatus.OK;

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

        if (hasRange) {
            headers.set(HttpHeaders.CONTENT_RANGE,
                    String.format("bytes %d-%d/%d", r.start, r.end, r.total)
            );
        }

        headers.setContentLength(r.length());
        headers.setContentType(
                MediaTypeFactory.getMediaType(key)
                        .orElse(MediaType.APPLICATION_OCTET_STREAM)
        );

        return ResponseEntity.status(status)
                .headers(headers)
                .body(is::transferTo);
    }

    /**
     * Разбирает и верифицирует JWT, проверяет срок жизни.
     *
     * @param token подписанный JWT
     * @return значение claim "key" (имя видео-файла)
     * @throws InvalidTokenException если токен невалиден или просрочен
     */
    private String parseAndValidate(String token) {
        try {
            Jws<Claims> claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return claims.getPayload().get("key", String.class);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid or expired token", e);
        }
    }

    /**
     * Исключение, выбрасываемое при неверном или просроченном токене.
     * Результатом будет HTTP 401 Unauthorized.
     */
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}