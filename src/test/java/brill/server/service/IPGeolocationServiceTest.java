package brill.server.service;

import static org.junit.Assert.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import brill.server.exception.IPGeoServiceException;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

@RunWith(JUnitPlatform.class)
@ExtendWith(MockitoExtension.class)

public class IPGeolocationServiceTest {

    static final Level LOG_LEVEL = Level.TRACE;

    IPGeolocationService service = null;

    @BeforeEach
    void setUp() {
        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.setLevel(LOG_LEVEL);
        // MockitoAnnotations.initMocks(this);
        service = new IPGeolocationService(true);
    }

    @Test
    public void locationLookup() throws Exception {
        System.out.println("Running locationLookup test");

        Map<String, String> result = service.findIPLocation("66.108.1.32");

        assertTrue(result != null);
        assertTrue(result.size() > 3);

        System.out.println("Location = " + result.toString());
    }

    @Test
    public void locationLookupBadRequest() throws Exception {
        System.out.println("Running locationLookupBadRequest test: Bad Request");

        try {
            service.findIPLocation("abcdefg");
            // Shouldn't get here
            assertTrue(false);
        } catch (IPGeoServiceException e) {
            assertTrue(e.getMessage().contains("invalid query"));
        }
    }

    @Disabled
    @Test
    public void usageLimit() throws Exception {
        System.out.println("Running usage limit test");

        try {
            for (int i = 0; i < 200; i++) {
                service.findIPLocation("66.108.1.32");
                Thread.sleep(200);
            }
            assertTrue(false);
        } catch (IPGeoServiceException e) {
            System.out.println("Exception msg: " + e.getMessage());
        }

        System.out.println("Trying after limit reached.");      
        int successCount = 0;
        for (int i = 0; i < 200; i++) {
            try {
                Thread.sleep(1000);
                
                Map<String, String> result = service.findIPLocation("66.108.1.32");
                if (result != null && successCount++ > 5) {
                    break;
                }
            } catch (IPGeoServiceException e) {
                System.out.println(e.getMessage());
            }
        }

        System.out.println("Finished usage limit test");
    }

    // we easliy comment out or change the url and check the http status
    // @Disabled
    // @Test
    // public void locationLookupHttpStatusResponse() throws Exception {
    //     System.out.println("Running locationLookup Http Forbidden Request Test");
    //     service.findIPLocation("66.108.1.32");
    //     assertEquals(403, service.getLastResponseStatus());
    //     System.out.println("The Http status code : " + service.getLastResponseStatus());
    // }

    // @Mock
    // private URL mock;

    // @Disabled
    // @Test
    // public void badRequest() throws Exception {
    //     System.out.println("Bad response code using mock.");
    //     when (mock.openConnection()).thenReturn(null);
    //     Map<String, String> badRequest = service.findIPLocation("66.108.1.32");
    //     assertNull(badRequest);
    // }

}