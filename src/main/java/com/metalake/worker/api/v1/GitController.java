package com.metalake.worker.api.v1;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class GitController {
    private final GitBridgeClient git;

    public record BranchRequest(@NotBlank String repositoryUrl, String credentialRef) {}

    @PostMapping("/api/git/branches")
    public GitBridgeClient.BranchResponse branches(@Valid @RequestBody BranchRequest request) {
        return git.branches(request.repositoryUrl(), request.credentialRef());
    }
}
