
package com.metalake.worker.domain.file.entity;

import com.metalake.worker.domain.job.entity.ProcessingJob;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Getter
@Table(name = "file_processing")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileProcessing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processing_job_id", nullable = false)
    private ProcessingJob processingJob;

    @Column(name = "file_path", nullable = false, columnDefinition = "text")
    private String filePath;

    @Column(name = "input_version", columnDefinition = "text")
    private String inputVersion;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(length = 100)
    private String processor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileProcessingStatus status = FileProcessingStatus.PENDING;

    @Column(name = "chunk_count", nullable = false)
    private Integer chunkCount = 0;

    @Column(name = "symbol_count", nullable = false)
    private Integer symbolCount = 0;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public FileProcessing(
            ProcessingJob processingJob,
            String filePath,
            String inputVersion,
            String checksumSha256,
            String processor
    ) {
        this.processingJob = Objects.requireNonNull(processingJob);
        this.filePath = Objects.requireNonNull(filePath);
        this.inputVersion = inputVersion;
        this.checksumSha256 = checksumSha256;
        this.processor = processor;
    }

    public void start() {
        validateStatus(FileProcessingStatus.PENDING);

        this.status = FileProcessingStatus.RUNNING;
        this.startedAt = Instant.now();
        this.finishedAt = null;
        this.errorMessage = null;
    }

    public void succeed(int chunkCount, int symbolCount) {
        validateStatus(FileProcessingStatus.RUNNING);

        if (chunkCount < 0 || symbolCount < 0) {
            throw new IllegalArgumentException("Counts must not be negative");
        }

        this.status = FileProcessingStatus.SUCCEEDED;
        this.chunkCount = chunkCount;
        this.symbolCount = symbolCount;
        this.finishedAt = Instant.now();
    }

    public void fail(String errorMessage) {
        validateStatus(FileProcessingStatus.RUNNING);

        this.status = FileProcessingStatus.FAILED;
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
    }

    public void skip() {
        validateStatus(FileProcessingStatus.PENDING);

        this.status = FileProcessingStatus.SKIPPED;
        this.finishedAt = Instant.now();
    }

    private void validateStatus(FileProcessingStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "Expected: " + expected + ", Actual: " + this.status
            );
        }
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
}
