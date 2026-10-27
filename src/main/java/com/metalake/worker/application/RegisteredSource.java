package com.metalake.worker.application;

public record RegisteredSource(
        Long sourceId,
        String metalake,
        String catalog,
        String schema,
        String fileset
) {}