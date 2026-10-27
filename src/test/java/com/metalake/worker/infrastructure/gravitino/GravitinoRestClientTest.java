
package com.metalake.worker.infrastructure.gravitino;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GravitinoRestClientIntegrationTest {

    @Autowired
    private GravitinoClient gravitinoClient;

    @Test
    void loadFilesetMetadata() {
        String catalog = "source";

        // 1. Schema 조회
        List<String> schemas = gravitinoClient.listSchemas(catalog);

        System.out.println("Schemas: " + schemas);
        assertFalse(schemas.isEmpty(), "No schemas in source catalog");

        boolean filesetLoaded = false;

        // 2. Fileset 목록 조회
        for (String schema : schemas) {
            List<String> filesets =
                    gravitinoClient.listFilesets(catalog, schema);

            System.out.println(
                    "Schema: " + schema + ", Filesets: " + filesets
            );

            if (filesets.isEmpty()) {
                continue;
            }

            // 3. Fileset 상세 조회
            String filesetName = filesets.get(0);

            GravitinoFileset fileset =
                    gravitinoClient.loadFileset(
                            catalog,
                            schema,
                            filesetName
                    );

            // 4. 저장 위치 확인
            String location = fileset.resolvedStorageLocation();

            System.out.println("Fileset: " + fileset.name());
            System.out.println("Type: " + fileset.type());
            System.out.println("Location: " + location);

            assertNotNull(location);
            assertFalse(location.isBlank());

            filesetLoaded = true;
            break;
        }

        assertTrue(
                filesetLoaded,
                "No fileset found. Register a test fileset first."
        );
    }

    @Test
    void discoverRemoteFilesets() {

        String catalog = "source";

        for (String schema : List.of("minio", "hdfs")) {

            List<String> names =
                    gravitinoClient.listFilesets(catalog, schema);

            System.out.println("Schema: " + schema);
            System.out.println("Filesets: " + names);

            for (String name : names) {

                GravitinoFileset fileset =
                        gravitinoClient.loadFileset(
                                catalog, schema, name
                        );

                System.out.println("  Name: " + fileset.name());
                System.out.println("  Type: " + fileset.type());
                System.out.println(
                        "  Locations: " + fileset.storageLocations()
                );
                System.out.println(
                        "  Storage Location: "
                                + fileset.resolvedStorageLocation()
                );
            }
        }
    }
}
