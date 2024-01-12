// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT License.
package brill.server.javaScriptHelper;

import java.sql.SQLException;
import javax.json.JsonArray;
import javax.json.JsonObject;
import brill.server.database.CachedConnection;
import brill.server.database.Database;
import brill.server.exception.SecurityServiceException;
import brill.server.service.PasswordService;
import brill.server.utils.JsonUtils;
import static java.lang.String.format;

/**
 * JavaScript Helper DB Class - provides access to the database.
 * 
 * Provides JavaScript with methods to execute queries and a method to hashing passwords, ready for storage in
 * the database.
 * 
 */
public class Db {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Db.class);

    public Database database = null;
    private boolean dbWriteAllowed;

    public Db(Database database, boolean dbWriteAllowed) {
        this.database = database;
        this.dbWriteAllowed = dbWriteAllowed;
    }

    public String executeQuery(String query, String jsonParams) throws SQLException {
        CachedConnection conn = null;
        try {
            log.trace(format("Executing query: %s", query + " jsonParams = " + jsonParams));

            conn = database.getConnection();
            JsonArray responseJson = conn.executeQuery(query, jsonParams);
            log.trace(format("Finished executing query. Result = %s", responseJson.toString()));
            return responseJson.toString();
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }

    public String executeNamedParamsQuery(String query, String jsonParams) throws SQLException {
        CachedConnection conn = null;

        try {
            conn = database.getConnection();
            JsonObject jsonObj = JsonUtils.jsonFromString(jsonParams);
            JsonArray responseJson = conn.executeNamedParametersQuery(query, jsonObj);
            return responseJson.toString();
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }

    public int executeNamedParamsUpdate(String query, String jsonParams) throws SQLException {
        CachedConnection conn = null;
        if (!dbWriteAllowed) {
            throw new SQLException("You require the db_write permission to perform that operation.");
        }
        try {
            conn = database.getConnection();
            JsonObject jsonObj = JsonUtils.jsonFromString(jsonParams);
            int rowCount = conn.executeNamedParametersUpdate(query, jsonObj);
            return rowCount;
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }

    public String hashPassword(String username, String password) throws SecurityServiceException {
        String hash =  PasswordService.hashPasswordForJavaScript(username, password);
        return hash;
    }

    public String removeSpecialChars(String str) {
        return str.replaceAll("[^a-zA-Z0-9_]", "");
    }

}