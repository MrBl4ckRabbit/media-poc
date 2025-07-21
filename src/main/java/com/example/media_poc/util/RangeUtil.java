package com.example.media_poc.util;

import org.springframework.http.HttpHeaders;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Утилитарный класс для работы с HTTP Range-запросами.
 * Предоставляет методы для парсинга заголовка Range из запроса
 * и формирования соответствующих заголовков ответа.
 */
public class RangeUtil {

    /**
     * Структура, описывающая диапазон запрошенных байт.
     *
     * start  — начальная позиция (включительно, 0-based)
     * end    — конечная позиция (включительно)
     * total  — общий размер ресурса в байтах
     */
    public static class Range {
        public long start;
        public long end;
        public long total;

        /**
         * @return фактическое количество байт в диапазоне (end-start+1)
         */
        public long length() {
            return end - start + 1;
        }

        /**
         * @return true, если диапазон покрывает не весь ресурс (для статуса 206)
         */
        public boolean isPartial() {
            return start > 0 || end < total - 1;
        }
    }

    /**
     * Регулярное выражение для разбора header Range вида "bytes=X-Y"
     * с необязательными числами (X или Y могут быть пустыми).
     */
    private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(\\d*)-(\\d*)");

    /**
     * Парсит заголовок HTTP Range и ограничивает диапазон размерами total.
     *
     * Если заголовок отсутствует или не соответствует формату, возвращается полный
     * диапазон [0..total-1].
     *
     * @param header значение заголовка Range из HTTP-запроса
     * @param total  полный размер ресурса в байтах
     * @return объект {@link Range} с вычисленными start, end и total
     */
    public static Range parse(String header, long total) {
        Range r = new Range();
        r.total = total;
        if (header == null || !header.startsWith("bytes=")) {
            r.start = 0;
            r.end = total - 1;
            return r;
        }
        Matcher m = RANGE_PATTERN.matcher(header);
        if (!m.find()) {
            r.start = 0;
            r.end = total - 1;
            return r;
        }
        String gs = m.group(1), ge = m.group(2);
        r.start = gs.isBlank() ? 0 : Long.parseLong(gs);
        r.end   = ge.isBlank() ? total - 1 : Long.parseLong(ge);
        if (r.end >= total) r.end = total - 1;
        return r;
    }

    /**
     * Формирует HTTP-заголовки для ответа:
     *  - Accept-Ranges: bytes
     *  - Content-Length: длина возвращаемого диапазона
     *  - (опционально) Content-Range: bytes start-end/total для частичного ответа
     *
     * @param r объект {@link Range} с описанием запрошенного диапазона
     * @return HttpHeaders готовые к установке в ResponseEntity
     */
    public static HttpHeaders buildHeaders(Range r) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        h.setContentLength(r.length());
        if (r.isPartial()) {
            h.set(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d",
                    r.start, r.end, r.total));
        }
        return h;
    }
}
