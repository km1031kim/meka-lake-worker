package com.metalake.worker.domain.source.entity;

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
@Table(name = "source_resource")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private SourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_mode", nullable = false, length = 30)
    private AccessMode accessMode;

    @Column(name = "gravitino_metalake", length = 128)
    private String gravitinoMetalake;

    @Column(name = "gravitino_catalog", length = 128)
    private String gravitinoCatalog;

    @Column(name = "gravitino_schema", length = 128)
    private String gravitinoSchema;

    @Column(name = "gravitino_fileset", length = 256)
    private String gravitinoFileset;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> configuration = new LinkedHashMap<>();

    @Column(name = "credential_ref", columnDefinition = "text")
    private String credentialRef;

    @Column(name = "current_published_job_id")
    private Long currentPublishedJobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceStatus status = SourceStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SourceResource(
            String name,
            SourceType sourceType,
            AccessMode accessMode
    ) {
        this.name = Objects.requireNonNull(name);
        this.sourceType = Objects.requireNonNull(sourceType);
        this.accessMode = Objects.requireNonNull(accessMode);
    }

    public void registerFileset(
            String metalake,
            String catalog,
            String schema,
            String fileset
    ) {
        this.gravitinoMetalake = metalake;
        this.gravitinoCatalog = catalog;
        this.gravitinoSchema = schema;
        this.gravitinoFileset = fileset;
    }

    public void updateConfiguration(Map<String, Object> configuration) {
        this.configuration = new LinkedHashMap<>(
                Objects.requireNonNull(configuration)
        );
    }

    public void publish(Long processingJobId) {
        this.currentPublishedJobId =
                Objects.requireNonNull(processingJobId);
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.status = SourceStatus.INACTIVE;
    }

    public void activate() {
        this.status = SourceStatus.ACTIVE;
    }

    public void connectFileset(
            String metalake,
            String catalog,
            String schema,
            String fileset
    ) {
        if (this.sourceType != SourceType.FILESET) {
            throw new IllegalStateException(
                    "Only FILESET source can connect to a Fileset"
            );
        }

        this.gravitinoMetalake = Objects.requireNonNull(metalake);
        this.gravitinoCatalog = Objects.requireNonNull(catalog);
        this.gravitinoSchema = Objects.requireNonNull(schema);
        this.gravitinoFileset = Objects.requireNonNull(fileset);
    }
}
