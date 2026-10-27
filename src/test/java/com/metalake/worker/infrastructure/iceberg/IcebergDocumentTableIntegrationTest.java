
package com.metalake.worker.infrastructure.iceberg;

import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class IcebergDocumentTableIntegrationTest {

    @Autowired
    private IcebergDocumentTableService tableService;

    @Autowired
    private Catalog icebergCatalog;

    @Test
    void createDocumentsTable() {

        Table table = tableService.createTableIfAbsent();

        TableIdentifier identifier = TableIdentifier.of(
                Namespace.of("ai_ready_poc"),
                "documents"
        );

        assertTrue(icebergCatalog.tableExists(identifier));

        assertEquals(10, table.schema().columns().size());

        assertNotNull(
                table.schema().findField("content_json")
        );

        System.out.println("Table: " + identifier);
        System.out.println("Location: " + table.location());
        System.out.println("Schema: " + table.schema());
    }
}
