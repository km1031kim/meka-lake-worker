package com.metalake.worker.api.v1;

import org.junit.jupiter.api.Test;

import static com.metalake.worker.api.v1.ApiModels.FilesetRef;
import static org.junit.jupiter.api.Assertions.*;

class GvfsSourceUriTest {
    @Test
    void externalFilesetRoot() {
        assertEquals("gvfs://fileset/source/minio/ai_minio/",
                GvfsSourceUri.from(new FilesetRef("ax", "source", "minio", "ai_minio")));
    }

    @Test
    void publishedGitMirrorRoot() {
        assertEquals("gvfs://fileset/git_managed/mirrors/git_source_25_job_28/",
                GvfsSourceUri.from(new FilesetRef("ax", "git_managed", "mirrors", "git_source_25_job_28")));
    }

    @Test
    void absentFilesetHasNoGvfsUri() {
        assertNull(GvfsSourceUri.from(null));
        assertNull(GvfsSourceUri.from(new FilesetRef("ax", null, "mirrors", "name")));
    }

    @Test
    void charactersAreUriEncoded() {
        assertEquals("gvfs://fileset/source/minio/report%20docs/",
                GvfsSourceUri.from(new FilesetRef("ax", "source", "minio", "report docs")));
    }
}
