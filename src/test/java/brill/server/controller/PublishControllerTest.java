package brill.server.controller;

// import brill.server.exception.GitServiceException;
import brill.server.service.GitService;

// import static org.junit.Assert.assertFalse;
// import static org.junit.Assert.assertTrue;
// import static org.junit.jupiter.api.Assertions.assertNotNull;
// import static org.junit.jupiter.api.Assertions.assertThrows;
// import static org.mockito.Mockito.when;
// import static org.mockito.ArgumentMatchers.any;
// import java.text.SimpleDateFormat;
// import java.time.LocalDateTime;
// import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
// import static java.lang.String.format;

@RunWith(JUnitPlatform.class)
@ExtendWith(MockitoExtension.class)
// @Slf4j
public class PublishControllerTest {

    static final Level LOG_LEVEL = Level.TRACE;

    @Mock
    private GitService gitService;

    @BeforeEach
    public void setUp() {
        final Logger logger = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.setLevel(LOG_LEVEL);
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void publishJson() throws Exception {

        // String json = "{'event': 'publish', '/test.json', 'content': 'Text message'}";
    }
}