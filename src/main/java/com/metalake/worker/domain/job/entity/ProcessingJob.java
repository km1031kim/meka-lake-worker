
package com.metalake.worker.domain.job.entity;

import com.metalake.worker.domain.source.entity.SourceResource;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Getter
@Table(name = "processing_job")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private SourceResource sourceResource;

    @Column(name = "job_type", nullable = false, length = 30)
    private String jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "source_revision", columnDefinition = "text")
    private String sourceRevision;

    @Column(name = "airflow_dag_id", length = 200)
    private String airflowDagId;

    @Column(name = "airflow_run_id", length = 300)
    private String airflowRunId;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProcessingJob(
            SourceResource sourceResource,
            String jobType,
            String sourceRevision,
            String idempotencyKey
    ) {
        this.sourceResource = Objects.requireNonNull(sourceResource);
        this.jobType = Objects.requireNonNull(jobType);
        this.sourceRevision = sourceRevision;
        this.idempotencyKey = idempotencyKey;
    }

    public void assignAirflowRun(String dagId, String runId) {
        this.airflowDagId = dagId;
        this.airflowRunId = runId;
    }

    public void start() {
        validateStatus(JobStatus.PENDING);

        this.status = JobStatus.RUNNING;
        this.startedAt = Instant.now();
        this.finishedAt = null;
        this.errorMessage = null;
    }

    public void succeed() {
        validateStatus(JobStatus.RUNNING);

        this.status = JobStatus.SUCCEEDED;
        this.finishedAt = Instant.now();
    }

    public void fail(String errorMessage) {
        validateStatus(JobStatus.RUNNING);

        this.status = JobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
    }

    private void validateStatus(JobStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "Invalid job status: expected=" + expected
                            + ", actual=" + this.status
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
