package com.metalake.worker.api.v1;

import com.metalake.worker.domain.source.repository.SourceResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.regex.Pattern;

import static com.metalake.worker.api.v1.ApiModels.*;

/** Registers the selected branch; clone/mirroring occurs later in a normalization Job. */
@Component
@RequiredArgsConstructor
public class GitSourceRegistrationHandler implements SourceRegistrationHandler {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SourceResourceRepository sources;
    private final GitBridgeClient git;

    @Override public boolean supports(String sourceType) {
        return "GIT".equalsIgnoreCase(sourceType);
    }

    @Override
    @Transactional
    public SourceResponse register(RegisterSourceRequest request) {
        Connection c = request.connection();
        if (c.repositoryUrl() == null || c.repositoryUrl().isBlank() ||
            c.ref() == null || c.ref().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Git repositoryUrl and ref(branch) are required");
        }
        var branches = git.branches(c.repositoryUrl(), c.credentialRef()).branches();
        if (branches == null || !branches.contains(c.ref())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Git branch does not exist");
        }
        // SourceResource already exists in the working repo. Persist only connection metadata,
        // never the access token; the private Git worker resolves credentialRef.
        String config = json.writeValueAsString(Map.of(
            "repositoryUrl", c.repositoryUrl(), "branch", c.ref()));
        Long id = jdbc.queryForObject("""
    INSERT INTO public.source_resource
        (name, source_type, access_mode,
         configuration, credential_ref, status)
    VALUES (?, 'GIT', 'MIRRORED',
            CAST(? AS jsonb), ?, 'ACTIVE')
    RETURNING id
    """,
                Long.class,
                request.name(),
                config,
                c.credentialRef()
        );
        return SourceController.toResponse(sources.findById(id).orElseThrow());
    }
}
