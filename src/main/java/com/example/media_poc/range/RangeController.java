package com.example.media_poc.range;


import com.example.media_poc.storage.VideoStorage;
import com.example.media_poc.util.RangeUtil;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;

/**
 * REST-контроллер для стриминга видео по HTTP Range-запросам.
 * <p>
 * Эндпоинты:
 * GET /range/media/{key}
 * - Отдаёт весь или частичный (206) контент видео-файла {key}.
 * - Разбирает заголовок Range и формирует соответствующие
 * Accept-Ranges, Content-Range и Content-Length.
 */
@RestController
@RequestMapping("/range/media")
public class RangeController {
    private final VideoStorage storage;

    public RangeController(VideoStorage storage) {
        this.storage = storage;
    }

    /**
     * Стримит видео-файл по указанному диапазону.
     *
     * @param key         имя или путь к видео-файлу
     * @param rangeHeader значение заголовка "Range", опционально
     * @return ResponseEntity с кодом 200 (полный файл) или 206 (частичный контент)
     * @throws IOException при ошибках чтения из хранилища
     */
    @GetMapping("/{key}")
    public ResponseEntity<StreamingResponseBody> stream(
            @PathVariable String key,
            @RequestHeader(value = "Range", required = false) String rangeHeader
    ) throws IOException {
        long size = storage.size(key);
        RangeUtil.Range r = RangeUtil.parse(rangeHeader, size);

        InputStream is = storage.readChunk(key, r.start, (int) r.length());

        HttpHeaders headers = RangeUtil.buildHeaders(r);
        headers.setContentType(MediaTypeFactory
                .getMediaType(key)
                .orElse(MediaType.APPLICATION_OCTET_STREAM)
        );

        return ResponseEntity
                .status(r.isPartial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .headers(headers)
                .body(is::transferTo);
    }
}
