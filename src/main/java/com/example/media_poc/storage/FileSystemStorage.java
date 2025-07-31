package com.example.media_poc.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * Реализация VideoStorage для локальной файловой системы.
 *
 * При создании принимает корневую директорию {@code media.storage.path}
 * из настроек Spring и хранит её в поле {@link #root}.
 *
 * Обеспечивает:
 *  - случайный доступ к байтам файлов через {@link #readChunk(String, long, int)}
 *  - получение полного размера файла через {@link #size(String)}
 *  - получение списка всех файлов в директории через {@link #listKeys()}
 */

@Service
@Profile("!s3")
public class FileSystemStorage implements VideoStorage {
    /**
     * Корневая директория, в которой лежат видео-файлы.
     * Заполняется значением из свойства "media.storage.path".
     */
    private final Path root;

    public FileSystemStorage(@Value("${media.storage.path}") String storagePath) {
        this.root = Paths.get(storagePath);
    }

    /**
     * Читает кусок видео-файла длиной {@code length} байт,
     * начиная с позиции {@code offset} (0-based).
     *
     * Открывает {@link RandomAccessFile} в режиме чтения,
     * делает {@code seek(offset)}, читает до {@code length} байт в буфер,
     * затем возвращает {@link ByteArrayInputStream}, обёрнутый на этот буфер.
     *
     * @param key    имя или относительный путь к видео-файлу под {@code root}
     * @param offset смещение в байтах, с которого начать чтение
     * @param length максимальное число байт для чтения
     * @return InputStream с фактически прочитанными данными (до конца файла может быть меньше)
     * @throws IOException при ошибках открытия или чтения файла
     */
    @Override
    public InputStream readChunk(String key, long offset, int length) throws IOException {
        Path file = root.resolve(key);
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(offset);
            byte[] buf = new byte[length];
            int read = raf.read(buf);
            return new ByteArrayInputStream(buf, 0, Math.max(read, 0));
        }
    }

    /**
     * Возвращает полный размер видео-файла в байтах.
     *
     * Использует {@link Files#size(Path)} для определения длины.
     *
     * @param key имя или относительный путь к видео-файлу под {@code root}
     * @return размер файла в байтах
     * @throws IOException при ошибках доступа к файлу
     */
    @Override
    public long size(String key) throws IOException {
        return Files.size(root.resolve(key));
    }

    /**
     * Возвращает список имён всех файлов в корневой директории {@code root}.
     *
     * Сканирует только верхний уровень (не рекурсивно!!!), фильтрует по регулярным файлам,
     * конвертирует {@link Path#getFileName()} в строку.
     *
     * @return список имён видео-файлов
     * @throws UncheckedIOException обёртывает возможные I/O ошибки в RuntimeException
     */
    @Override
    public List<String> listKeys() {
        try (Stream<Path> files = Files.list(root)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Ошибка при перечислении файлов в " + root, e);
        }
    }
}
