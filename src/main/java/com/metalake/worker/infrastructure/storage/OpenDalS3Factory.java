package com.metalake.worker.infrastructure.storage;


import com.metalake.worker.infrastructure.gravitino.GravitinoS3Credentials;
import com.metalake.worker.infrastructure.gravitino.S3CatalogSettings;
import org.apache.opendal.Operator;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Component
public class OpenDalS3Factory {

    public Operator create(
            String storageLocation,
            S3CatalogSettings settings,
            GravitinoS3Credentials credentials
    ) {
        URI uri = URI.create(storageLocation);

        if (!"s3a".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(
                    "Unsupported storage URI: " + uri.getScheme()
            );
        }

        String bucket = uri.getHost();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("Missing bucket");
        }

        String prefix = uri.getPath();
        String root = prefix == null || prefix.equals("/")
                || prefix.isBlank()
                ? "/"
                : "/" + prefix.replaceAll("^/+|/+$", "") + "/";

        Map<String, String> config = new HashMap<>();

        config.put("bucket", bucket);
        config.put("root", root);
        config.put("endpoint", settings.endpoint());
        config.put("region", settings.region());

        config.put("access_key_id", credentials.accessKeyId());
        config.put("secret_access_key", credentials.secretAccessKey());

        // 다른 AWS 환경변수나 로컬 프로파일 사용 방지
        config.put("disable_config_load", "true");
        config.put("disable_ec2_metadata", "true");

        return Operator.of("s3", config);
    }
}
