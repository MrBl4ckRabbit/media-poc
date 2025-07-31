package com.example.media_poc.controllers;

import com.example.media_poc.storage.CachedMediaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST-контроллер для управления каталогом видео.
 *
 * Предоставляет единственный эндпоинт:
 *   GET /media
 * который возвращает JSON-массив строк — список всех доступных ключей (имён файлов)
 * в хранилище VideoStorage.
 *
 * Используется клиентским JS для динамического формирования плейлистов
 * без хардкода имён и количества роликов.
 */

@RestController
@RequestMapping("/media")
public class MediaController {

    private final CachedMediaService cachedMediaService;

    public MediaController(CachedMediaService cachedMediaService) {
        this.cachedMediaService = cachedMediaService;
    }

    @GetMapping
    public List<String> listAll() {
        return cachedMediaService.getCachedKeys().stream()
                .filter(k -> k.startsWith("videos/"))
                .map(k -> k.substring("videos/".length()))
                .toList();
    }
}
