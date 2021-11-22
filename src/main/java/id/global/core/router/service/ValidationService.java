package id.global.core.router.service;

import java.io.IOException;
import java.io.UncheckedIOException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.rest.client.RegistryClientFactory;

@ApplicationScoped
public class ValidationService {
    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    @Inject
    ObjectMapper objectMapper;

    public void getSchemas() {
        var client = RegistryClientFactory.create("https://schema.internal.globalid.dev/apis/registry/v2");
        var results = client.listArtifactsInGroup("id.global.event.model");

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
