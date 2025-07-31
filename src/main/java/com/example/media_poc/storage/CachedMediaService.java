package com.example.media_poc.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class CachedMediaService {
    private final VideoStorage storage;
    private final List<String> cachedKeys = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public CachedMediaService(VideoStorage storage,
                              @Value("${media.cache.refresh:30}") int refreshSeconds) {
        this.storage = storage;
        refreshCache();
        scheduler.scheduleAtFixedRate(this::refreshCache, refreshSeconds, refreshSeconds, TimeUnit.SECONDS);
    }

    private void refreshCache() {
        try {
            List<String> keys = storage.listKeys();
            cachedKeys.clear();
            cachedKeys.addAll(keys);
        } catch (Exception e) {
            // Логируем ошибку, но не падаем
        }
    }

    public List<String> getCachedKeys() {
        return new ArrayList<>(cachedKeys);
    }
}
