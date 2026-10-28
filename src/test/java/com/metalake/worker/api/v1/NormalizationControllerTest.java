package com.metalake.worker.api.v1;

import com.metalake.worker.api.v1.persistence.ApiNormalizedOutputRepository;
import com.metalake.worker.domain.job.repository.ProcessingJobRepository;
import com.metalake.worker.domain.source.entity.SourceResource;
import com.metalake.worker.domain.source.entity.SourceType;
import com.metalake.worker.domain.source.repository.SourceResourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.List;
import java.util.Optional;
import static com.metalake.worker.api.v1.ApiModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NormalizationControllerTest {
    @Test
    void returnsAcceptedWithPollUrl() {
        var states = mock(NormalizationJobStateService.class);
        var worker = mock(NormalizationWorker.class);
        var executor = mock(ThreadPoolTaskExecutor.class);
        var jobs = mock(ProcessingJobRepository.class);
        var sources = mock(SourceResourceRepository.class);
        var outputs = mock(ApiNormalizedOutputRepository.class);
        when(states.create(15L)).thenReturn(123L);
        var controller = new NormalizationController(states, worker, executor, jobs, sources, outputs);
        var response = controller.normalize(15L, new NormalizeRequest(List.of("a.pdf")));
        assertEquals(202, response.getStatusCode().value());
        assertEquals("/api/normalizations/123", response.getBody().statusUrl());
        verify(executor).execute(any(Runnable.class));
    }

    @Test
    void newFilesetWithNoPublishedJobIsNotReady() {
        var states = mock(NormalizationJobStateService.class);
        var worker = mock(NormalizationWorker.class);
        var executor = mock(ThreadPoolTaskExecutor.class);
        var jobs = mock(ProcessingJobRepository.class);
        var sources = mock(SourceResourceRepository.class);
        var outputs = mock(ApiNormalizedOutputRepository.class);
        var source = mock(SourceResource.class);
        when(source.getSourceType()).thenReturn(SourceType.FILESET);
        when(sources.findById(15L)).thenReturn(Optional.of(source));
        var controller = new NormalizationController(states, worker, executor, jobs, sources, outputs);
        PublishedResults results = controller.published(15L);
        assertEquals("NOT_READY", results.status());
        assertTrue(results.outputs().isEmpty());
    }
}
