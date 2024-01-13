package brill.server.service;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.util.Random;
import javax.json.JsonArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import brill.server.database.Database;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

@RunWith(JUnitPlatform.class)
@ExtendWith(MockitoExtension.class)

public class SessionLoggerServiceTest {

    static final Level LOG_LEVEL = Level.TRACE;
    Database db;
    DatabaseService dbService;
    IPGeolocationService locationService;
    SessionLoggerService service = null;

    @BeforeEach
    void setUp() {
        final Logger logger = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.setLevel(LOG_LEVEL);

        String driver = "com.mysql.cj.jdbc.Driver";
        String url = "jdbc:mysql://localhost:3306/brill_local_db";
        String username = System.getenv("BRILL_LOCAL_DATABASE_USERNAME");
        assertNotNull(username);
        String password = System.getenv("BRILL_LOCAL_DATABASE_PWD");
        assertNotNull(password);
        db = new Database(driver, url, username, password);

        dbService = new DatabaseService(db);
        locationService = new IPGeolocationService(true);
        service = new SessionLoggerService(true, dbService, locationService);
    }

    @Test
    public void logTestRecord() throws Exception {
        System.out.println("Running Session Logger test");

        String sessionId = "test_id-" + randomId();
        service.logNewSession(sessionId, "User Agent Test Header", "66.108.1.32");

        // Check row is in DB.
        JsonArray result = dbService.query("select * from session_log where session_id = '" + sessionId + "'", null);
        assertTrue(result.size() == 1);
  
        System.out.println("Finished");
    }

    private String randomId() {
        String characters = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder id = new StringBuilder();
        Random rnd = new Random();
        for (int i = 0; i < 6; i++) {
            int index = rnd.nextInt(characters.length());
            id.append(characters.charAt(index));
        }
        return id.toString();
    }
}