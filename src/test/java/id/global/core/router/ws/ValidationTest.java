package id.global.core.router.ws;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import id.global.core.router.service.ValidationService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@Disabled
public class ValidationTest {

    @Inject
    ValidationService validationService;

    @Test
    public void testDelivery() {
        validationService.getSchemas();
    }

}
