// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT License.
package brill.server.service;

import javax.json.JsonArray;
import javax.json.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import brill.server.database.CachedConnection;
import brill.server.database.Database;
import static java.lang.String.format;
import java.sql.SQLException;
import java.time.LocalDateTime;


/**
 * Database Services
 */
@Service
public class DatabaseService {

    Database database;

    public DatabaseService(@Autowired @Qualifier("database") Database database) {
        this.database = database;
    }

    public String query(String query) {
        CachedConnection conn = null;
        try {
            conn = database.getConnection();
            String responseJson = conn.executeQuery(query).toString();
            return responseJson;
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }

    public JsonArray query(String query, String jsonParams) throws SQLException {
        CachedConnection conn = null;
        try {
            conn = database.getConnection();
            JsonArray responseJson = conn.executeQuery(query, jsonParams);
            return responseJson;
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }

    public JsonArray queryUsingNamedParameters(String query, JsonObject jsonParams) throws SQLException {
        CachedConnection conn = null;
        try {
            conn = database.getConnection();
            JsonArray responseJson = conn.executeNamedParametersQuery(query, jsonParams);
            return responseJson;
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }

    public int executeUpdate(String query, String jsonParams) throws SQLException {
        CachedConnection conn = null;
        try {
            conn = database.getConnection();
            int rowsUpdated  = conn.executeUpdate(query, jsonParams);
            return rowsUpdated;
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    } 

    public int executeNamedParametersUpdate(String sql, JsonObject jsonParams) throws SQLException {
        CachedConnection conn = null;
        try {
            conn = database.getConnection();
            int rowsUpdated = conn.executeNamedParametersUpdate(sql, jsonParams);
            return rowsUpdated;
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }

    public JsonObject getUserDetails(String user) throws SQLException {
        CachedConnection conn = null;
        try {
            conn = database.getConnection();

            JsonArray responseJson = conn.executeQuery("select * from brill_cms_user where username = ? and deleted != 'Y'",
                    format("{\"username\": \"%s\"}", user));
  
            if (responseJson.size() > 0) {
                return responseJson.getJsonObject(0);
            }

            return null;
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }

    public void updateUserDetails(String user, String newPwdHash) throws SQLException {
        CachedConnection conn = null;
        try {
            conn = database.getConnection();

            int rowsUpdated = conn.executeUpdate("update brill_cms_user set password = ?, changePassword = 'N' where username = ?",
                    format("{\"password\": \"%s\", \"username\": \"%s\"}", newPwdHash, user));

            if (rowsUpdated != 1) {
                throw new SQLException("Failed to update password.");
            }
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }

    public void updateLastLoginDateTime(String user) throws SQLException {
        CachedConnection conn = null;
        try {
            conn = database.getConnection();

            int rowsUpdated = conn.executeUpdate("update brill_cms_user set last_login = ? where username = ?",
                format("{\"last_login\": \"%s\", \"username\": \"%s\"}", LocalDateTime.now().toString() , user));
                   
            if (rowsUpdated != 1) {
                throw new SQLException("Failed to update login details.");
            }
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }

}