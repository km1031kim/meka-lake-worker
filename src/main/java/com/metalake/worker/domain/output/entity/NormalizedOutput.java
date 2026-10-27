
package com.metalake.worker.domain.output.entity;

import com.metalake.worker.domain.job.entity.ProcessingJob;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Entity
@Getter
@Table(name = "normalized_output")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NormalizedOutput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processing_job_id", nullable = false)
    private ProcessingJob processingJob;

    @Column(name = "output_type", nullable = false, length = 50)
    private String outputType;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private StorageType storageType;

    @Column(name = "metalake_name", length = 128)
    private String metalakeName;

    @Column(name = "catalog_name", length = 128)
    private String catalogName;

    @Column(name = "namespace_name", length = 128)
    private String namespaceName;

    @Column(name = "object_name", length = 256)
    private String objectName;

    @Column(name = "location_uri", nullable = false, columnDefinition = "text")
    private String locationUri;

    @Column(name = "row_count")
    private Long rowCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> properties = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public NormalizedOutput(
            ProcessingJob processingJob,
            String outputType,
            StorageType storageType,
            String locationUri
    ) {
        this.processingJob = Objects.requireNonNull(processingJob);
        this.outputType = Objects.requireNonNull(outputType);
        this.storageType = Objects.requireNonNull(storageType);
        this.locationUri = Objects.requireNonNull(locationUri);
    }

    public void assignCatalog(
            String metalakeName,
            String catalogName,
            String namespaceName,
            String objectName
    ) {
        this.metalakeName = metalakeName;
        this.catalogName = catalogName;
        this.namespaceName = namespaceName;
        this.objectName = objectName;
    }

    public void recordResult(
            long rowCount,
            Map<String, Object> properties
    ) {
        if (rowCount < 0) {
            throw new IllegalArgumentException("Row count must not be negative");
        }

        this.rowCount = rowCount;
        this.properties = new LinkedHashMap<>(
                Objects.requireNonNull(properties)
        );
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
