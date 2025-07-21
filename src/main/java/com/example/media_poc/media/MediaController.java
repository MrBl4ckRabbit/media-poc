package com.example.media_poc.media;

import com.example.media_poc.storage.VideoStorage;
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

    private final VideoStorage storage;

    public MediaController(VideoStorage storage) {
        this.storage = storage;
    }

    /**
     * Возвращает список всех ключей (имён файлов) в хранилище.
     *
     * Клиент может использовать этот список для построения URL к RangeController
     * или TokenController.
     *
     * @return список строк — имена видео-файлов
     */
    @GetMapping
    public List<String> listAll() {
        return storage.listKeys();
    }
}
