
package com.metalake.worker.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocalStorageReaderTest {

    @TempDir
    Path tempDir;

    private final LocalStorageReader reader = new LocalStorageReader();

    @Test
    void listAndReadFile() throws Exception {

        // 1. 테스트 파일 생성
        Path file = tempDir.resolve("sample.txt");

        Files.writeString(
                file,
                "Hello Meta Lake",
                StandardCharsets.UTF_8
        );

        // 2. 디렉터리 URI 변환
        URI directoryUri = tempDir.toUri();

        assertTrue(reader.supports(directoryUri));

        // 3. 파일 목록 조회
        List<URI> files = reader.listFiles(directoryUri);

        assertEquals(1, files.size());

        // 4. 실제 파일 읽기
        try (InputStream inputStream = reader.open(files.get(0))) {

            String content = new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );

            assertEquals("Hello Meta Lake", content);
        }
    }
}
