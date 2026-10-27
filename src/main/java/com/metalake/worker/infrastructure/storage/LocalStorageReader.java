
package com.metalake.worker.infrastructure.storage;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Component
public class LocalStorageReader implements StorageReader {

    @Override
    public boolean supports(URI location) {
        return "file".equalsIgnoreCase(location.getScheme());
    }

    @Override
    public List<URI> listFiles(URI directoryUri) throws IOException {
        Path directory = toPath(directoryUri);

        if (!Files.isDirectory(directory)) {
            throw new NotDirectoryException(directory.toString());
        }

        try (Stream<Path> paths = Files.list(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(Path::toUri)
                    .sorted(Comparator.comparing(URI::toString))
                    .toList();
        }
    }

    @Override
    public InputStream open(URI fileUri) throws IOException {
        Path path = toPath(fileUri);

        if (!Files.isRegularFile(path)) {
            throw new IOException("Not a regular file: " + path);
        }

        return Files.newInputStream(path);
    }

    private Path toPath(URI uri) {
        if (!supports(uri)) {
            throw new IllegalArgumentException(
                    "Unsupported storage URI: " + uri
            );
        }

        // file:/data/storage 같은 파일 URL을 java의 Path 객체로 변환
        return Path.of(uri);
    }
}
