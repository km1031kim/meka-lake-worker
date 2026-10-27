package com.metalake.worker.infrastructure.gravitino;

import java.util.List;
import java.util.Map;

public interface GravitinoClient {

    List<String> listSchemas(String catalog);

    List<String> listFilesets(String catalog, String schema);

    GravitinoFileset loadFileset(
            String catalog,
            String schema,
            String fileset
    );

    void createExternalFileset(
            String catalog,
            String schema,
            String filesetName,
            String storageLocation,
            Map<String, String> properties
    );
}