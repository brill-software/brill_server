package brill.server.service;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    //temporary we could only send 30 request each minute
    @Test
    public void locationLookupOverload() throws Exception{
        System.out.println("Running locationLookupOverload test");
        for(int i=0; i<29; i++){
            Map<String,String> result = service.findIPLocation("66.108.1.32");
            assertTrue(result != null);
            assertTrue(result.size() == 3);
            System.out.println(i+ ". Location = " + result.toString());
        }
    }
    @Test
    public void locationLookupBadRequest() throws Exception{
        System.out.println("Running locationLookupBadRequest test: Bad Request");
        Map<String,String> BadRequestResult = service.findIPLocation("abcdefg");
        assertTrue(BadRequestResult == null);
        System.out.println("Running locationLookupBadRequest test: Empty Request");
        Map<String,String> EmptyRequestResult = service.findIPLocation("");
        assertTrue(EmptyRequestResult != null);
        assertTrue(EmptyRequestResult.size()==3);
    }
    //we easliy comment out or change the url and check the http  status
    @Test
    public void locationLookupHttpStatusResponse() throws Exception{
        System.out.println("Running locationLookup Http Forbidden Request Test");
        service.findIPLocation("66.108.1.32"); 
        assertEquals(403, service.getLastResponseStatus());
        System.out.println("The Http status code : "+service.getLastResponseStatus());
    }
}