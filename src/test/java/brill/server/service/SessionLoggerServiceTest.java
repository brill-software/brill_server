package brill.server.service;

import static org.junit.Assert.assertTrue;
import java.time.LocalDateTime;
import java.util.Random;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
        
        // LOCAL DB
        String url = "jdbc:mysql://localhost:3306/brill_local_db";
        String username = System.getenv("BRILL_LOCAL_DATABASE_USERNAME");
        String password = System.getenv("BRILL_LOCAL_DATABASE_PWD");

        // PRODUCTION DB
        // String url = "jdbc:mysql://localhost:3306/brill_prod_db";
        // String username = "chris";
        // String password = "Mysql1234";

        db = new Database(driver, url, username, password);
        dbService = new DatabaseService(db);
        locationService = new IPGeolocationService(true);
        service = new SessionLoggerService(true, dbService, locationService);
    }

    @Disabled
    @Test
    public void logTestRecord() throws Exception {
        System.out.println("Running Session Logger test");

        String sessionId = "test_id-" + randomId();
        // service.logNewSession(sessionId, "User Agent Test Header", "66.108.1.32");
        service.logNewSession(sessionId, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "188.141.52.136");
        
        // Check row is in DB.
        JsonArray result = dbService.query("select * from session_log where session_id = '" + sessionId + "'", null);
        assertTrue(result.size() == 1);
  
        System.out.println("Running log end session test");
        JsonObjectBuilder objBuilder=Json.createObjectBuilder();
        JsonObject jsonParams= objBuilder.add("sessionId", sessionId)
            .add("endDateTime",LocalDateTime.now().toString()).build();
        dbService.executeNamedParametersUpdate("update session_log set end_date_time = :endDateTime where session_id = :sessionId", jsonParams);
        // Check row is in DB.
        JsonArray result2 = dbService.query("select * from session_log where session_id = '" + sessionId + "'", null);
        assertTrue(!result2.getJsonObject(0).getString("end_date_time").isEmpty());
          
        System.out.println("Finished");
    }

    @Disabled
    @Test
    public void disabledServiceTest() throws Exception {
        System.out.println("Running disabled service test");
        String sessionId = "test_id-" + randomId();
        
        // Set the service to disabled
        service = new SessionLoggerService(false, dbService, locationService);

        // This should not throw an exception and should not log to the database
        service.logNewSessionToDb(sessionId, "User Agent Test Header", "66.108.1.32");

        // Check that there are no rows in the database for the provided session ID
        JsonArray result = dbService.query("select * from session_log where session_id = '" + sessionId + "'", null);
        assertTrue(result.isEmpty());
        System.out.println("Finished");
    }

    @Disabled
    @Test
    public void invalidIPQuertTest() throws Exception{
        System.out.println("Running invalid IP query test");
        
        String sessionId = "test_id-" + randomId();
        //set the invalid IP address
        service.logNewSession(sessionId, "User Agent Test Header", "1.16.63.255.0.0.0");
        
        // Check the response from api is empty in DB.
        JsonArray result = dbService.query("select * from session_log where session_id = '" + sessionId + "'", null);
        assertTrue(result.size() == 0);
        System.out.println("Finished");
    }

    @Disabled
    @Test
    public void duplicateSessionIDTest() throws Exception{
        System.out.println("Running duplicate session ID test");
        //both request will use the same session ID
        
        String sessionId = "test_id-" + randomId();
        String sqlCheckSessionCount = "SELECT COUNT(*) FROM session_log WHERE session_id = :sessionId";
        String sqlCheckSession = "SELECT * FROM session_log WHERE session_id = :sessionId";
        //first request
        service.logNewSession(sessionId, "User Agent Test Header", "1.16.63.250");
        JsonArray result = dbService.query("select * from session_log where session_id = '" + sessionId + "'", null);        
        // Ensure the result is not empty before accessing its elements
        assertTrue(!result.isEmpty());
        JsonObject firstSessionLog = result.getJsonObject(0);
        String firstCountry = firstSessionLog.getString("country");
        String firstCity = firstSessionLog.getString("city");
        String firstRegion = firstSessionLog.getString("region");
        //second request
        service.logNewSession(sessionId, "User Agent Test Header", "66.108.2.32");
        JsonArray result2 = dbService.query("select * from session_log where session_id = '" + sessionId + "'", null);
        // Ensure the result is not empty before accessing its elements
        assertTrue(!result2.isEmpty());

        JsonObjectBuilder objBuilderCheck = Json.createObjectBuilder();
                objBuilderCheck.add("sessionId", sessionId);
                JsonObject jsonParamsCheck = objBuilderCheck.build();
        //the reponse from database should be matcched the first requset
        assertTrue(dbService.queryUsingNamedParameters(sqlCheckSessionCount, jsonParamsCheck).getJsonObject(0).getInt("COUNT(*)")==1);
        assertTrue(dbService.queryUsingNamedParameters(sqlCheckSession, jsonParamsCheck).getJsonObject(0).getString("country").equals(firstCountry));
        assertTrue(dbService.queryUsingNamedParameters(sqlCheckSession, jsonParamsCheck).getJsonObject(0).getString("city").equals(firstCity));
        assertTrue(dbService.queryUsingNamedParameters(sqlCheckSession, jsonParamsCheck).getJsonObject(0).getString("region").equals(firstRegion));

        System.out.println("Finished");
    }

    // TO BE REMOVED - code for fixing db.
    //
    // @Disabled
    // @Test
    // public void addMissingUserAgentData() throws Exception{
    //     System.out.println("Running add missing user agent ID");

    //     service.addMissingUserAgentId();
        
    //     System.out.println("Finished.");
    // }

    // @Disabled
    // @Test
    // public void addMissingSessionLength() throws Exception{
    //     System.out.println("Running add missing session length");

    //     service.addMissingSessionLength();
        
    //     System.out.println("Finished.");
    // }

    // @Disabled
    // @Test
    // public void addMissingVisitsAndPages() throws Exception{
    //     System.out.println("Running add messing Visits and Pages");

    //     service.addMissingVisitsAndPages();
        
    //     System.out.println("Finished.");
    // }

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