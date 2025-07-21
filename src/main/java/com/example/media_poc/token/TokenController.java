package com.example.media_poc.token;

import com.example.media_poc.storage.VideoStorage;
import com.example.media_poc.util.RangeUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.crypto.SecretKey;
import java.io.InputStream;
import java.time.Instant;
import java.util.Date;
import java.util.Map;


/**
 * REST-контроллер для защищённого стриминга через JWT.
 *
 * Предоставляет два эндпоинта:
 *   1) GET /token/media/{key}/token
 *      — генерация и выдача JWT, содержащего claim "key" с именем видео.
 *
 *   2) GET /token/media/signed/{token}
 *      — разбор и верификация JWT, затем стриминг видео по диапазону
 *        так же, как в RangeController.
 */
@RestController
@RequestMapping("/token/media")
public class TokenController {
    private final VideoStorage storage;
    private final SecretKey secretKey = Jwts.SIG.HS256.key().build();

    public TokenController(VideoStorage storage) {
        this.storage = storage;
    }
    /**
     * Генерирует JWT для доступа к видео-файлу {key}.
     *
     * @param key имя видео-файла
     * @return JSON { "token": "<jwt>" }
     */
    @GetMapping("/{key}/token")
    public Map<String, String> issueToken(@PathVariable String key) {
        Instant now = Instant.now();
        String jws = Jwts.builder()
                .claim("key", key)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .signWith(secretKey)
                .compact();
        return Map.of("token", jws);
    }

    /**
     * Отдаёт видео-файл по защищённому JWT и HTTP Range.
     *
     * @param token       JWT, содержащий claim "key"
     * @param rangeHeader заголовок Range, опционально
     * @return ResponseEntity с содержимым видео (200 или 206)
     * @throws Exception при неверном или истёкшем токене, либо ошибках I/O
     */
    @GetMapping("/signed/{token}")
    public ResponseEntity<StreamingResponseBody> streamByToken(
            @PathVariable String token,
            @RequestHeader(value = "Range", required = false) String rangeHeader
    ) throws Exception {
        Jws<Claims> claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);

        String key = claims.getPayload().get("key", String.class);
        long size = storage.size(key);
        RangeUtil.Range range = RangeUtil.parse(rangeHeader, size);

        InputStream is = storage.readChunk(key, range.start, (int) range.length());
        HttpHeaders headers = RangeUtil.buildHeaders(range);
        headers.setContentType(
                MediaTypeFactory.getMediaType(key)
                        .orElse(MediaType.APPLICATION_OCTET_STREAM)
        );
        return ResponseEntity
                .status(range.isPartial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .headers(headers)
                .body(is::transferTo);
    }
}