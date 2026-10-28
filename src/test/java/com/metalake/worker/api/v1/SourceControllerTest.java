package com.metalake.worker.api.v1;

import com.metalake.worker.application.RegisteredSource;
import com.metalake.worker.application.SourceRegistrationService;
import com.metalake.worker.domain.source.entity.AccessMode;
import com.metalake.worker.domain.source.entity.SourceResource;
import com.metalake.worker.domain.source.entity.SourceStatus;
import com.metalake.worker.domain.source.entity.SourceType;
import com.metalake.worker.domain.source.repository.SourceResourceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import static com.metalake.worker.api.v1.ApiModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SourceControllerTest {
    @Test
    void registerMinioUsingExistingRegistrationService() {
        SourceRegistrationService registration = mock(SourceRegistrationService.class);
        SourceResourceRepository repository = mock(SourceResourceRepository.class);
        SourceResource entity = mock(SourceResource.class);
        when(registration.registerMinio(any())).thenReturn(
                new RegisteredSource(15L, "ax", "source_15", "object-storage", "fileset_15"));
        when(repository.findById(15L)).thenReturn(Optional.of(entity));
        when(entity.getId()).thenReturn(15L);
        when(entity.getName()).thenReturn("manuals");
        when(entity.getSourceType()).thenReturn(SourceType.FILESET);
        when(entity.getAccessMode()).thenReturn(AccessMode.DIRECT);
        when(entity.getStatus()).thenReturn(SourceStatus.ACTIVE);
        when(entity.getGravitinoFileset()).thenReturn("fileset_15");
        when(entity.getGravitinoMetalake()).thenReturn("ax");
        when(entity.getGravitinoCatalog()).thenReturn("source_15");
        when(entity.getGravitinoSchema()).thenReturn("object-storage");

        var connection = new Connection("MINIO", "http://minio.local:9000", "docs", "pdf/",
                "key", "secret", null, null, null, null, null, null);
        var response = new SourceController(List.of(new MinioSourceRegistrationHandler(registration, repository)), repository)
                .register(new RegisterSourceRequest("manuals", "S3", connection));

        assertEquals(201, response.getStatusCode().value());
        assertEquals(15L, response.getBody().sourceId());
        assertEquals("FILESET", response.getBody().sourceType());
        assertTrue(response.getBody().capabilities().normalizable());
        verify(registration).registerMinio(any());
    }
}
