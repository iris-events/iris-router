package org.iris_events.router.ws;

import org.iris_events.router.service.ValidationService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@Disabled
public class ValidationTest {

    @Inject
    ValidationService validationService;

    @Test
    public void testDelivery() {

    }

}
