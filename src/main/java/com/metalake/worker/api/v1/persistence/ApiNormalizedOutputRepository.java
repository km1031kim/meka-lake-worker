package com.metalake.worker.api.v1.persistence;

import com.metalake.worker.domain.output.entity.NormalizedOutput;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApiNormalizedOutputRepository extends JpaRepository<NormalizedOutput, Long> {
    List<NormalizedOutput> findByProcessingJob_Id(Long jobId);
}
