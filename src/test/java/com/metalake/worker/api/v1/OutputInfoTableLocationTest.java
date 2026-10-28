package com.metalake.worker.api.v1;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutputInfoTableLocationTest {
    @Test
    void newConstructorContainsPhysicalTableLocation() {
        ApiModels.OutputInfo info = new ApiModels.OutputInfo(
                "DOCUMENTS", "ICEBERG", "ai-ready-catalog", "ai_ready_poc",
                "documents", 23L, 1L, 5754249644672803605L,
                null, "s3://data-platform/warehouse/ai_ready_poc/documents");
        assertNull(info.datasetUri());
        assertEquals("s3://data-platform/warehouse/ai_ready_poc/documents", info.tableLocation());
    }

    @Test
    void legacyConstructorKeepsSourceCompatibility() {
        ApiModels.OutputInfo info = new ApiModels.OutputInfo(
                "VECTORS", "LANCE", "lance_poc", "normalized",
                "source_24_job_23", 23L, 24L, null,
                "s3://data-platform/lance/source-24/job-23.lance/");
        assertNull(info.tableLocation());
        assertNotNull(info.datasetUri());
    }
}
