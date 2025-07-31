package com.example.media_poc.storage;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;

@Service
public class VideoMetadataCache {
    private final VideoStorage storage;


    public VideoMetadataCache(VideoStorage storage) {
        this.storage = storage;
    }

    /**
     * Будет кэшировать результат на 10 минут (прописано в spring.cache.caffeine.spec).
     */
    @Cacheable(cacheNames = "videoSize", key = "#key")
    public long getSize(String key) {
        try {
            return storage.size(key);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
