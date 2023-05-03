// © 2005 Brill Software Limited - Brill Framework, distributed under the MIT license.
package brill.server.database;

import java.io.StringReader;
import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import javax.json.*;
import javax.json.stream.JsonParsingException;
import static java.lang.String.format;

/**
 * A cached database connection.
 *
 */
public class CachedConnection implements Connection {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CachedConnection.class);

    private boolean inUse;
    private Connection conn;
    private Statement stmt = null;
    private ResultSet rset = null;

    private String url;
    private String username;
    private String password;
    private long lastInUseChange;

    CachedConnection(Connection conn, String url, String username, String password) {
        inUse = true;
        this.conn = conn;
        this.url = url;
        this.username = username;
        this.password = password;
        lastInUseChange = System.currentTimeMillis();
    }

    boolean equals(String url, String username, String password) {
        if (password.equals(this.password) && username.equals(this.username) && url.equals(this.url))
            return true;

        return false;
    }

    boolean isInUse() {
        return inUse;
    }

    void setInUse(boolean inUse) {
        lastInUseChange = System.currentTimeMillis();
        this.inUse = inUse;
    }

    long getLastInUseChange() {
        return lastInUseChange;
    }

    public String getUrl() {
        return url + "," + username;
    }

    public String getUsername() {
        return username;
    }

    void closeConnection() {
        try {
            conn.close();
        } catch (SQLException e) {
            log.error("Unable to close database connection: " + e.getMessage());
        }

    }

    public void close() {
        setInUse(false);
    }

    private void reset() {
        if (rset != null) {
            try {
                rset.close();
            } catch (Exception e) {
            }
            rset = null;
        }

        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException se) {
            }
            stmt = null;
        }

    }

    /**
     * Takes in a prepared statement and a set of JSON parameter values and executes
     * the query. The results are returned as a JSON string.
     * 
     * @param sql
     * @return JSON string containing the result set.
     */
    public JsonArray executeQuery(String sql, String jsonParameters) throws SQLException {
        try {
            reset();
            PreparedStatement stmt = conn.prepareStatement(sql);

            if (jsonParameters != null && jsonParameters.length() > 0) {
                JsonReader reader = Json.createReader(new StringReader(jsonParameters));
                JsonObject jsonObject = reader.readObject();
                int index = 1;
                for (Map.Entry<String, JsonValue> entry : jsonObject.entrySet()) {
                    JsonValue value = entry.getValue();
                    switch (value.getValueType()) {
                        case STRING:
                            stmt.setString(index, ((JsonString) value).getString());
                            break;
                        case NUMBER:
                            stmt.setInt(index, ((JsonNumber) value).intValue());
                            break;
                        case NULL:
                            stmt.setNull(index, Types.NVARCHAR);
                            break;
                        case FALSE:
                            stmt.setString(index, "false");
                            break;
                        case TRUE:
                            stmt.setString(index, "true");
                            break;
                        case OBJECT:
                            log.debug("Unexpected Object encountered in JSON.");
                            throw new SQLException("Unexpected Object encountered in JSON.");
                        case ARRAY:
                            log.debug("Unexpected Array encountered in JSON.");
                            throw new SQLException("Unexpected Array encountered in JSON.");
                        default:
                            log.debug("Unexpected type encountered in JSON.");
                            throw new SQLException("Unexpected type encountered in JSON.");
                    }
                    index++;
                }
            }
            rset = stmt.executeQuery();
            JsonArray jsonArray = getJsonArrayFromResultSet(rset);
            return jsonArray;
        } catch (JsonParsingException e) {
            log.error(format("Json parsing exception: %s\n%s", e.getMessage(), jsonParameters));
            throw new SQLException("JSON parsing error. Please see the server log for more details.");
        }
    }

    /**
     * Takes in a named parameter SQL query and set of JSON parameter values and
     * executes the query. The results are returned as a JSON string. The JSON
     * object passed in must contains values that match the named parameters.
     * 
     * Example:
     * 
     * select * from employee limit :offset, :row_count
     * 
     * { "offset": 0, "row_count": 10 }
     * 
     * 
     * @param sql SQL that has named parameters.
     * @return JSON string containing the result set.
     */
    public JsonArray executeNamedParametersQuery(String sql, JsonObject jsonObject) throws SQLException {
        log.debug(sql);
        reset();
        NamedPreparedStatement stmt = NamedPreparedStatement.prepareStatement(conn, sql, jsonObject);
        rset = stmt.executeQuery();
        JsonArray jsonArray = getJsonArrayFromResultSet(rset);
        return jsonArray;
    }

    /**
     * Returns the results as a JSON string.
     * 
     * @param sql
     * @return
     */
    public JsonArray executeQuery(String sql) {
        try {
            // log.trace("Query SQL = " + sql);
            reset();
            stmt = conn.createStatement();
            rset = stmt.executeQuery(sql);
            JsonArray jsonArray = getJsonArrayFromResultSet(rset);
            return jsonArray;
        } catch (SQLException se) {
            log.trace("sql = " + sql, se);
        }
        return null;
    }

    private JsonArray getJsonArrayFromResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int count = metaData.getColumnCount();
        String[] columnName = new String[count];
        JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();
        while (rs.next()) {
            JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
            for (int i = 1; i <= count; i++) {
                columnName[i - 1] = metaData.getColumnLabel(i);
                int colType = metaData.getColumnType(i);
                if (colType == Types.INTEGER || colType == Types.BIGINT) {
                    jsonObjectBuilder.add(columnName[i - 1], rs.getInt(i));
                } else {
                    String str = rs.getString(i);
                    if (str != null) {
                        jsonObjectBuilder.add(columnName[i - 1], str);
                    } else {
                        jsonObjectBuilder.addNull(columnName[i - 1]);
                    }
                }
            }
            JsonObject jsonObject = jsonObjectBuilder.build();
            jsonArrayBuilder.add(jsonObject);
        }
        return jsonArrayBuilder.build();
    }

    public int executeUpdate(String sql) throws SQLException {
        int retValue = 0;
        // Log.write(Log.TRACE,"Update SQL = " + sql);
        reset();
        stmt = conn.createStatement();
        retValue = stmt.executeUpdate(sql);
        return retValue;
    }

    public int executeUpdate(String sql, String jsonParameters) throws SQLException {
        try {
            reset();
            PreparedStatement stmt = conn.prepareStatement(sql);

            if (jsonParameters != null && jsonParameters.length() > 0) {
                JsonReader reader = Json.createReader(new StringReader(jsonParameters));
                JsonObject jsonObject = reader.readObject();
                int index = 1;
                for (Map.Entry<String, JsonValue> entry : jsonObject.entrySet()) {
                    JsonValue value = entry.getValue();
                    switch (value.getValueType()) {
                        case STRING:
                            stmt.setString(index, ((JsonString) value).getString());
                            break;
                        case NUMBER:
                            stmt.setInt(index, ((JsonNumber) value).intValue());
                            break;
                        case NULL:
                            stmt.setNull(index, Types.NVARCHAR);
                            break;
                        case FALSE:
                            stmt.setString(index, "false");
                            break;
                        case TRUE:
                            stmt.setString(index, "true");
                            break;
                        case OBJECT:
                            log.debug("Unexpected Object encountered in JSON.");
                            throw new SQLException("Unexpected Object encountered in JSON.");
                        case ARRAY:
                            log.debug("Unexpected Array encountered in JSON.");
                            throw new SQLException("Unexpected Array encountered in JSON.");
                        default:
                            log.debug("Unexpected type encountered in JSON.");
                            throw new SQLException("Unexpected type encountered in JSON.");
                    }
                    index++;
                }
            }
            int retValue = stmt.executeUpdate();
            return retValue;
            
        } catch (JsonParsingException e) {
            log.error(format("ExecuteUpdate Json parsing exception: %s\n%s", e.getMessage(), jsonParameters));
            throw new SQLException("JSON parsing error. Please see the server log for more details.");
        }

    }

    /**
     * Takes in a named parameter SQL query and set of JSON parameter values and
     * executes an update. The SQL must be for an UPDATE, INSERT, DELETE or DDL
     * statement. The number of rows updated or deleted is reutrned.
     * 
     * Example:
     * 
     * update brill_cms_user name = :name where user_id = :user_id
     * 
     * { "name": "Ali" }
     * 
     * @param sql SQL that has named parameters.
     * @return The number of rows updated, inserted or deleted.
     */
    public int executeNamedParametersUpdate(String sql, JsonObject jsonObject) throws SQLException {
        log.debug(sql);
        reset();
        NamedPreparedStatement stmt = NamedPreparedStatement.prepareStatement(conn, sql, jsonObject);
        int rowCount = stmt.executeUpdate();
        return rowCount;
    }

    private void renewConnection() {
        try {
            conn = DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            log.warn("Unable to renew database connection: " + e.getMessage());
        }
    }

    public Statement createStatement() throws SQLException {
        return conn.createStatement();
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return conn.prepareStatement(sql);
    }

    public CallableStatement prepareCall(String sql) throws SQLException {
        return conn.prepareCall(sql);
    }

    public String nativeSQL(String sql) throws SQLException {
        return conn.nativeSQL(sql);
    }

    public void setAutoCommit(boolean autoCommit) throws SQLException {
        try {
            conn.setAutoCommit(autoCommit);
        }

        catch (SQLException e) {
            // The connection is probably closed, so get a new connection
            log.error("Renewing connection due to database error: " + e.getMessage());
            renewConnection();
            conn.setAutoCommit(autoCommit);
        }
    }

    public boolean getAutoCommit() throws SQLException {
        return conn.getAutoCommit();
    }

    public void commit() throws SQLException {
        conn.commit();
    }

    public void rollback() throws SQLException {
        conn.rollback();
    }

    public boolean isClosed() throws SQLException {
        return conn.isClosed();
    }

    public DatabaseMetaData getMetaData() throws SQLException {
        return conn.getMetaData();
    }

    public void setReadOnly(boolean readOnly) throws SQLException {
        conn.setReadOnly(readOnly);
    }

    public boolean isReadOnly() throws SQLException {
        return conn.isReadOnly();
    }

    public void setCatalog(String catalog) throws SQLException {
        conn.setCatalog(catalog);
    }

    public String getCatalog() throws SQLException {
        return conn.getCatalog();
    }

    public void setTransactionIsolation(int level) throws SQLException {
        conn.setTransactionIsolation(level);
    }

    public int getTransactionIsolation() throws SQLException {
        return conn.getTransactionIsolation();
    }

    public SQLWarning getWarnings() throws SQLException {
        return conn.getWarnings();
    }

    public void clearWarnings() throws SQLException {
        conn.clearWarnings();
    }

    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return conn.createStatement();
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
            throws SQLException {
        return conn.prepareStatement(sql, resultSetType, resultSetConcurrency);
    }

    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return conn.prepareCall(sql, resultSetType, resultSetConcurrency);
    }

    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return conn.getTypeMap();
    }

    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        conn.setTypeMap(map);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return conn.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return conn.isWrapperFor(iface);
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        conn.setHoldability(holdability);
    }

    @Override
    public int getHoldability() throws SQLException {
        return conn.getHoldability();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        return conn.setSavepoint();
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        return conn.setSavepoint(name);
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        conn.rollback(savepoint);
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        conn.releaseSavepoint(savepoint);
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        return conn.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
            int resultSetHoldability) throws SQLException {
        return conn.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
            int resultSetHoldability) throws SQLException {
        return conn.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        return conn.prepareStatement(sql, autoGeneratedKeys);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        return conn.prepareStatement(sql, columnIndexes);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        return conn.prepareStatement(sql, columnNames);
    }

    @Override
    public Clob createClob() throws SQLException {
        return conn.createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        return conn.createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        return conn.createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return conn.createSQLXML();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        return conn.isValid(timeout);
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        conn.setClientInfo(name, value);

    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        conn.setClientInfo(properties);

    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        return conn.getClientInfo(name);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return conn.getClientInfo();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return conn.createArrayOf(typeName, elements);
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return conn.createStruct(typeName, attributes);
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        conn.setSchema(schema);
    }

    @Override
    public String getSchema() throws SQLException {
        return conn.getSchema();
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        conn.abort(executor);

    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        conn.setNetworkTimeout(executor, milliseconds);
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return conn.getNetworkTimeout();
    }
}