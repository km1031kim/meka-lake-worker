package com.metalake.worker.api.v1;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GitNormalizationWorker {
    private final NormalizationJobStateService states;
    private final JdbcTemplate jdbc;
    private final GitBridgeClient git;

    public void run(Long jobId) {
        try {
            Long sourceId = states.start(jobId);
            var source = jdbc.queryForMap("""
                SELECT configuration->>'repositoryUrl' AS repository_url,
                       configuration->>'branch' AS branch,
                       credential_ref
                FROM public.source_resource WHERE id=? AND source_type='GIT'
                """, sourceId);
            var result = git.normalize(new GitBridgeClient.GitJobRequest(
                sourceId, jobId, (String) source.get("repository_url"),
                (String) source.get("branch"), (String) source.get("credential_ref")));
            if (result.processedPaths() != null) {
                for (String path : result.processedPaths()) {
                    Long fileId = states.startGitFile(jobId, path, result.sourceRevision());
                    states.succeedFile(fileId);
                }
            }
            states.publishGit(jobId, result);
        } catch (Exception e) {
            log.error("Git normalization failed. jobId={}", jobId, e);
            states.fail(jobId, e);
        }
    }
}
