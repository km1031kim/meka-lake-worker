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


    void createFilesetCatalog(
            String catalog,
            Map<String, String> properties
    );

    void createSchema(
            String catalog,
            String schema
    );

    S3CatalogSettings loadS3CatalogSettings(String catalog);

    GravitinoS3Credentials loadS3Credentials(
            String catalog,
            String schema,
            String fileset
    );
}