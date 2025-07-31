package com.example.media_poc.controllers;


import com.example.media_poc.storage.VideoMetadataCache;
import com.example.media_poc.storage.VideoStorage;
import com.example.media_poc.util.RangeUtil;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;

/**
 * Контроллер для HTTP Range-стриминга видео.
 *
 * Эндпоинты:
 *   HEAD /range/media/{key}
 *     • Возвращает только заголовки:
 *       - Accept-Ranges: bytes
 *       - Content-Length: полный размер видео
 *     • Позволяет клиенту узнать размер и поддержку Range без загрузки тела.
 *
 *   GET /range/media/{key}
 *     • Обрабатывает заголовок Range (например, "bytes=1000-5000"):
 *       - Если запроса нет или он без явного end ("bytes=X-"),
 *         отдаёт первый чанк фиксированного размера (1 МБ).
 *       - Иначе отдаёт точно запрошенный диапазон байт.
 *     • Устанавливает заголовки:
 *       - Accept-Ranges: bytes
 *       - Content-Range: bytes start-end/total (для частичного ответа)
 *       - Content-Length: длина возвращаемого диапазона
 *       - Content-Type: медиа-тип, определённый по ключу
 *     • Возвращает статус:
 *       - 206 Partial Content при наличии Range-запроса
 *       - 200 OK при полном GET (без заголовка Range)
 */
@RestController
@RequestMapping("/range/media")
public class RangeController {
    private final VideoStorage storage;
    private final VideoMetadataCache metadataCache;

    public RangeController(VideoStorage storage, VideoMetadataCache metadataCache) {
        this.storage = storage;
        this.metadataCache = metadataCache;
    }

    /**
     * HEAD-запрос для получения метаданных видео.
     *
     * @param key имя (ключ) видео-файла
     * @return ResponseEntity без тела, но с заголовками
     *         Accept-Ranges: bytes
     *         Content-Length: полный размер ресурса
     */
    @RequestMapping(value = "/{key}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> head(@PathVariable String key) {
        long size = metadataCache.getSize(key);

        HttpHeaders h = new HttpHeaders();
        h.set("Accept-Ranges", "bytes");
        h.setContentLength(size);

        return ResponseEntity
                .ok()
                .headers(h)
                .build();
    }

    /**
     * GET-запрос для стриминга полного или частичного контента.
     *
     * @param key         имя (ключ) видео-файла
     * @param rangeHeader значение заголовка "Range" из запроса (может быть null)
     * @return потоковый ответ StreamingResponseBody с нужными заголовками
     * @throws IOException при ошибках чтения потока
     */
    @GetMapping("/{key}")
    public ResponseEntity<StreamingResponseBody> stream(
            @PathVariable String key,
            @RequestHeader(value = "Range", required = false) String rangeHeader
    ) throws IOException {

        long size        = metadataCache.getSize(key);
        RangeUtil.Range r = RangeUtil.parse(rangeHeader, size);


        if (rangeHeader != null && rangeHeader.endsWith("-")) {
            long maxChunk = 1024 * 1024L;
            long end = Math.min(r.start + maxChunk - 1, r.total - 1);
            r = new RangeUtil.Range(r.start, end, r.total);
        }

        InputStream is = storage.readChunk(key, r.start, (int) r.length());

        boolean hasRange = rangeHeader != null;
        HttpStatus status = hasRange
                ? HttpStatus.PARTIAL_CONTENT
                : HttpStatus.OK;

        HttpHeaders h = new HttpHeaders();
        h.set("Accept-Ranges", "bytes");
        if (hasRange) {
            h.set("Content-Range",
                    String.format("bytes %d-%d/%d", r.start, r.end, r.total)
            );
        }
        h.setContentLength(r.length());
        h.setContentType(MediaTypeFactory
                .getMediaType(key)
                .orElse(MediaType.APPLICATION_OCTET_STREAM));

        return ResponseEntity.status(status)
                .headers(h)
                .body(is::transferTo);
    }
}
