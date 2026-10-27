
package com.metalake.worker.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

public interface StorageReader {

    // 해당 URI를 처리할 수 있는지 확인
    boolean supports(URI location);

    // 디렉터리 내 파일 목록 조회
    List<URI> listFiles(URI directoryUri) throws IOException;

    //	파일을 열어서 InputStream 반환
    InputStream open(URI fileUri) throws IOException;
}
