package org.iris_events.router.service;

import jakarta.enterprise.context.ApplicationScoped;

/*
 *  todo fetch json schema from schema registry and validate payloads against it
 */
@ApplicationScoped
public class ValidationService {
   /* private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

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

    }*/

}
