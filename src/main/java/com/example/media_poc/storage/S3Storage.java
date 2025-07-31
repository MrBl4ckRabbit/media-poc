package com.example.media_poc.storage;

import com.example.media_poc.exception.VideoStorageException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.InputStream;
import java.util.List;

@Service
@Profile("s3")
public class S3Storage implements VideoStorage {
    private final S3Client s3Client;
    private final String bucketName;

    public S3Storage(S3Client s3Client, @Value("${aws.s3.bucket}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }


    @Override
    @CircuitBreaker(name = "videoStorage", fallbackMethod = "onReadChunkFailure")
    public InputStream readChunk(String key, long offset, int length) {
        String realKey = "videos/" + key;
        String range = "bytes=" + offset + "-" + (offset + length - 1);
        return s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucketName)
                .key(realKey)
                .range(range)
                .build());
    }

    public InputStream onReadChunkFailure(String key, long offset, int length, Throwable t) {
        throw new VideoStorageException("Не удалось прочитать фрагмент видео " + key, t);
    }

    @Override
    @CircuitBreaker(name = "videoStorage", fallbackMethod = "onSizeFailure")
    public long size(String key) {
        String realKey = "videos/" + key;
        return s3Client.headObject(HeadObjectRequest.builder()
                        .bucket(bucketName)
                        .key(realKey)
                        .build())
                .contentLength();
    }

    public long onSizeFailure(String key, Throwable t) {
        throw new VideoStorageException("Не удалось получить размер видео " + key, t);
    }

    @Override
    @CircuitBreaker(name = "videoStorage", fallbackMethod = "onListKeysFailure")
    public List<String> listKeys() {
        return s3Client.listObjectsV2Paginator(ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .build())
                .contents().stream()
                .map(S3Object::key)
                .toList();
    }

    public List<String> onListKeysFailure(Throwable t) {
        throw new VideoStorageException("Не удалось перечислить ключи в бакете " + bucketName, t);
    }
}