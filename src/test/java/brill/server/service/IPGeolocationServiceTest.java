package brill.server.service;

import static org.junit.Assert.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

@RunWith(JUnitPlatform.class)
@ExtendWith(MockitoExtension.class)

public class IPGeolocationServiceTest {

    static final Level LOG_LEVEL = Level.TRACE;

    IPGeolocationService service = null;

    @BeforeEach
    void setUp() {
        final Logger logger = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.setLevel(LOG_LEVEL);
        service = new IPGeolocationService(true);
    }

    @Test
    public void locationLookup() throws Exception {
        System.out.println("Running locationLookup test");

        Map<String,String> result = service.findIPLocation("66.108.1.32");

        assertTrue(result != null);
        assertTrue(result.size() == 3);

        System.out.println("Location = " + result.toString());
    }
}