package brill.server.database;

import java.io.StringReader;
import java.util.Map;

import javax.json.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import static java.lang.String.format;

@RunWith(JUnitPlatform.class)
@ExtendWith(MockitoExtension.class)
public class DatabaseTest {

    static final Level LOG_LEVEL = Level.INFO;

    Database db;
    CachedConnection conn;
    
    @BeforeEach
    void setUp() {
        final Logger logger = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.setLevel(LOG_LEVEL);
        db = new Database();

        String driver = "com.mysql.cj.jdbc.Driver";
        String url = "jdbc:mysql://localhost:3306/user_db";
        String username = "chris";
        String password = "Mysql1234";
        conn = db.getConnection(driver, url, username, password);
    }

    @AfterEach
    void finished() {
        conn.close();
        System.out.println("Finished.");
    }

    @Test
    public void testJsonParsing() throws Exception {

        String json = "{\"username\": \"chris\", \"last_name\": \"Bulcock\" }";

        JsonReader reader = Json.createReader(new StringReader(json));

        JsonObject jsonObject = reader.readObject();

        for (Map.Entry<String, JsonValue> entry : jsonObject.entrySet()) {
            System.out.println(format("%s = %s", entry.getKey(),entry.getValue()));
        }

        System.out.println(format("Username = %s", jsonObject.getString("username")));
        System.out.println(format("Username = %s", jsonObject.getString("last_name")));
    }

    @Test
    public void testQueryWithJsonParams() throws Exception {
        // String jsonParameters = "{\"username\": \"chris\", \"last_name\": \"Bulcock\" }";
        // JsonArray jsonResult = conn.executeQuery("select * from user_table where username = ? and last_name = ?", jsonParameters);
        // System.out.println(jsonResult.toString());
    }
}