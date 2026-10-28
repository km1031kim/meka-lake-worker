package com.metalake.worker.api.v1;

import static com.metalake.worker.api.v1.ApiModels.*;

/** Extend this for GIT, DATABASE, HDFS without changing the public controller. */
public interface SourceRegistrationHandler {
    boolean supports(String requestedSourceType);
    SourceResponse register(RegisterSourceRequest request);
}
