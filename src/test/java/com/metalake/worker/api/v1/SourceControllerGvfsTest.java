package com.metalake.worker.api.v1;

import com.metalake.worker.domain.source.entity.AccessMode;
import com.metalake.worker.domain.source.entity.SourceResource;
import com.metalake.worker.domain.source.entity.SourceStatus;
import com.metalake.worker.domain.source.entity.SourceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SourceControllerGvfsTest {
    @Test
    void filesetSourceReturnsRegisteredFilesetRoot() {
        SourceResource source = mock(SourceResource.class);
        when(source.getId()).thenReturn(18L);
        when(source.getName()).thenReturn("sample");
        when(source.getSourceType()).thenReturn(SourceType.FILESET);
        when(source.getAccessMode()).thenReturn(AccessMode.DIRECT);
        when(source.getStatus()).thenReturn(SourceStatus.ACTIVE);
        when(source.getGravitinoMetalake()).thenReturn("ax");
        when(source.getGravitinoCatalog()).thenReturn("source");
        when(source.getGravitinoSchema()).thenReturn("minio");
        when(source.getGravitinoFileset()).thenReturn("ai_minio");

        assertEquals("gvfs://fileset/source/minio/ai_minio/",
                SourceController.toResponse(source).originalGvfsUri());
    }

    @Test
    void publishedGitSourceReturnsItsMirrorFilesetRoot() {
        SourceResource source = mock(SourceResource.class);
        when(source.getId()).thenReturn(25L);
        when(source.getName()).thenReturn("repo");
        when(source.getSourceType()).thenReturn(SourceType.GIT);
        when(source.getAccessMode()).thenReturn(AccessMode.MIRRORED);
        when(source.getStatus()).thenReturn(SourceStatus.ACTIVE);
        when(source.getGravitinoMetalake()).thenReturn("ax");
        when(source.getGravitinoCatalog()).thenReturn("git_managed");
        when(source.getGravitinoSchema()).thenReturn("mirrors");
        when(source.getGravitinoFileset()).thenReturn("git_source_25_job_28");

        assertEquals("gvfs://fileset/git_managed/mirrors/git_source_25_job_28/",
                SourceController.toResponse(source).originalGvfsUri());
    }

    @Test
    void gitBeforeFirstPublicationHasNoMirrorUri() {
        SourceResource source = mock(SourceResource.class);
        when(source.getId()).thenReturn(25L);
        when(source.getName()).thenReturn("repo");
        when(source.getSourceType()).thenReturn(SourceType.GIT);
        when(source.getAccessMode()).thenReturn(AccessMode.MIRRORED);
        when(source.getStatus()).thenReturn(SourceStatus.ACTIVE);

        assertNull(SourceController.toResponse(source).originalGvfsUri());
    }
}
