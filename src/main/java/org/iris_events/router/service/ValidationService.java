package org.iris_events.router.service;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.rest.client.RegistryClientFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ValidationService {
    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    @Inject
    ObjectMapper objectMapper;

    private String schemaRegistryUrl = "";
    private String artifactGroupId = "";

    public void getSchemas() {
        var client = RegistryClientFactory.create(schemaRegistryUrl);
        var results = client.listArtifactsInGroup(artifactGroupId);

        results.getArtifacts()
                .forEach(sa -> {
                    log.info("fetching schema for {}", sa);

                    try (var stream = client.getLatestArtifact(sa.getGroupId(), sa.getId());) {
                        JsonNode schema = objectMapper.readTree(stream);

                        log.info("content: {}", schema);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }

                });
        log.info("schemas: {}", results);

    }

}
