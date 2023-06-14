// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT license.
package brill.server.database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.sql.*;
import javax.json.*;
import javax.json.JsonValue.ValueType;
import brill.server.utils.SqlUtils;

/**
 * Named Parameters Pre-Prepared Statements class.
 *
 */
public class NamedPreparedStatement extends PreparedStatementImpl {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NamedPreparedStatement.class);

    private enum FormatType {

        NULL, BOOLEAN, BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, BIGDECIMAL, STRING, STRINGLIST, DATE, TIME, TIMESTAMP
    }

    private String equivalentSQL;
    private final List<String> lstParameters;
    private final List<String> directInsertParameters;
    private Set<String> notProcessedParameters;

    /**
     * Creates a Prepared Statement and adds the parameters supplied. The parameters must be named, not annonymous (question marks).
     * Parameters names must be pre-fixed by a ':' character or '::'. A double colon is used to directly insert a value into the SQL.
     * 
     * For example:
     * 
     * sql:     select * from employee limit :offset, :row_count
     * json:  {"offset": 10, "row_count": 10}
     *  
     * This is converted to:  select * from employee limit ?, ?  and the JSON values added as parameters.
     * 
     * SQL imposes restrictions on where parameters can occur. Column names can't be supplied as parameters. To get around this a
     * double colon ( '::' ) can be used to inject a value directly into the SQL string before a Prepared Statement is created.
     * 
     * For example:
     * 
     * sql:     select * from employee order by ::sortCol ::sortDirection limit :offset, :row_count
     * filter:  {"sortCol": "department", "sortDirection": "asc", "offset":0 , "row_count": 10}
     * 
     * This is converted to:  select * from employee order by department asc limit ?, ?
     * 
     * Single colon parameters are safe and not at risk from SQL Injection attacks. With double colon parameters, the values
     * are checked to ensure they conform to the same rules as Java variable names. No spaces or special characters are allowed. Care must be taken
     * however to only use double colon parameters when necessary and only for column names and search ordering. Use for table names could allow
     * access to all tables and is not recommended.
     * 
     * The SQL that is executed is a Prepared Statement that has the parameters as question marks. The equivalent SQL with the JSON values
     * subsituted in is logged for debug purposes at trace level. This can be cut and pasted into MySql Workbench or Oracle SQL Developer. 
     * 
     * @param conn
     * @param sql
     * @param json
     * @return
     * @throws SQLException
     */
    public static NamedPreparedStatement prepareStatement(Connection conn, String sqlWithComments, JsonObject json) throws SQLException {
        List<String> orderedParameters = new ArrayList<String>();
        List<String> directInsertParameters = new ArrayList<String>();
        String sql = SqlUtils.stripComments(sqlWithComments);
        int length = sql.length();
        StringBuffer parsedQuery = new StringBuffer(length);
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < length; i++) {
            char c = sql.charAt(i);
            if (inSingleQuote) {
                if (c == '\'') {
                    inSingleQuote = false;
                }
            } else if (inDoubleQuote) {
                if (c == '"') {
                    inDoubleQuote = false;
                }
            } else if (c == '\'') {
                inSingleQuote = true;
            } else if (c == '"') {
                inDoubleQuote = true;
            } else if (c == ':' && i + 1 < length && Character.isJavaIdentifierStart(sql.charAt(i + 1))) {
                int j = i + 2;
                while (j < length && Character.isJavaIdentifierPart(sql.charAt(j))) {
                    j++;
                }
                String name = sql.substring(i + 1, j);
                orderedParameters.add(name);
                c = '?';
                i += name.length();
            } else if (c == ':' && i + 2 < length && sql.charAt(i + 1) == ':' && Character.isJavaIdentifierStart(sql.charAt(i + 2))) {
                int j = i + 3;
                while (j < length && Character.isJavaIdentifierPart(sql.charAt(j))) {
                    j++;
                }
                String name = sql.substring(i + 2, j);
                String value = getDirectInsertValue(json, name);
                directInsertParameters.add(name);
                sql = sql.substring(0, i) + value + sql.substring(j);
                length = sql.length();
                parsedQuery.append(value);
                i += value.length() - 1;
                continue;
            }
            parsedQuery.append(c);       
        }

        NamedPreparedStatement stmt = new NamedPreparedStatement(conn.prepareStatement(parsedQuery.toString()), sql, orderedParameters, directInsertParameters);
        getParamsFromJsonObject(stmt, json);
        
        log.trace("Named Param SQL : " + sql);
        log.trace("Json values   : " + json.toString());
        log.trace("Prepared Stmt   : " + parsedQuery.toString());
        log.trace("Equivalent SQL  : " + stmt.equivalentSQL);

        return stmt;
    }

    /**
     * Checks values inserted into the SQL using :: to make sure they only contain a column name or word. This is to gaurd
     * against SQL injection attacks. 
     * 
     * @param jsonObject
     * @param key
     * @return
     * @throws SQLException
     */
    private static String getDirectInsertValue(JsonObject jsonObject, String key) throws SQLException {
        JsonValue value = jsonObject.get(key);
        if (value == null || value.getValueType() != ValueType.STRING) {
            throw new SQLException(String.format("Sql parameter ::%s must have a corresponding value of type STRING.", key));
        }
        String valueStr = ((JsonString) value).getString();
        if (!isValidIdentifier(valueStr)) {
            log.error(String.format("Parameter ::%s must only include [a-zA-Z0-9_]. Value = \"%s\"", key, valueStr ));
            throw new SQLException(String.format("Sql parameter ::%s must have a value that only includes [a-zA-Z0-9_] and doesn't start with a digit, dash or underscore. Value = %s",
                key, valueStr));
        }
        return valueStr;
    }

    /**
     * Checks values inserted into the SQL using :: to make sure they only contain a column name or word. This is to gaurd
     * against SQL injection attacks. The value must be a valid Java identifier. It must start with a letter and the following
     * characters can be letters, digits, dashes and underscores. No spaces are allowed.
     * 
     * @param jsonObject
     * @param key
     * @return
     * @throws SQLException
     */
    public static boolean isValidIdentifier(String s) {
        if (s.isEmpty()) {
            return true;
        }
        if (!Character.isJavaIdentifierStart(s.charAt(0))) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static void getParamsFromJsonObject(NamedPreparedStatement stmt, JsonObject jsonObject) throws SQLException {
        for (Map.Entry<String, JsonValue> entry : jsonObject.entrySet()) {
            if (stmt.directInsertParameters.contains(entry.getKey()) || entry.getKey().equals("columns") || entry.getKey().equals("filter_list")) {
                continue;
            }
            JsonValue value =  entry.getValue();
            switch (value.getValueType()) {
                case STRING: 
                    stmt.setString(entry.getKey(),((JsonString) value).getString());
                    break;
                case NUMBER:
                    stmt.setInt(entry.getKey(), ((JsonNumber) value).intValue());
                    break;
                case NULL:
                    stmt.setNull(entry.getKey(), Types.NVARCHAR);
                    break;
                case FALSE:
                    stmt.setString(entry.getKey(), "false");
                    break;
                case TRUE:
                    stmt.setString(entry.getKey(), "true");
                    break;
                case OBJECT:
                    log.debug(String.format("Unexpected Object encountered in JSON named %s", entry.getKey()));
                    throw new SQLException("Unexpected Object encountered in JSON.");
                case ARRAY:
                    log.debug("Unexpected Array encountered in JSON.");
                    throw new SQLException("Unexpected Array encountered in JSON.");
                default:
                    log.debug("Unexpected type encountered in JSON.");
                    throw new SQLException("Unexpected type encountered in JSON.");
            }
            stmt.notProcessedParameters.remove(entry.getKey());
        }
        if (stmt.notProcessedParameters.size() > 0) {
            throw new SQLException(String.format("Json is missing %s.", stmt.notProcessedParameters.toString()));
        }
    }

    private NamedPreparedStatement(PreparedStatement preparedStatement, String originalSQL, List<String> orderedParameters, List<String> directInsertParameters) {
        super(preparedStatement);
        this.equivalentSQL = originalSQL.trim();
        this.lstParameters = orderedParameters;
        this.directInsertParameters = directInsertParameters;
        this.notProcessedParameters = new HashSet<String>(orderedParameters);
    }

    private Collection<Integer> getParameterIndexes(String parameter) {
        Collection<Integer> indexes = new ArrayList<Integer>();
        for (int i = 0; i < lstParameters.size(); i++) {
            if (lstParameters.get(i).equalsIgnoreCase(parameter)) {
                indexes.add(i + 1);
            }
        }
        if (indexes.isEmpty()) {
            throw new IllegalArgumentException(String.format("The JSON contains the key '%s' but the SQL doesn't use this parameter.",
                     parameter));
        }
        return indexes;
    }

    public void setNull(String parameter, int sqlType) throws SQLException {
        for (Integer i : getParameterIndexes(parameter)) {
            getPreparedStatement().setNull(i, sqlType);
            this.equivalentSQL = this.equivalentSQL.replaceFirst("(?i):" + parameter, Matcher.quoteReplacement(format((String) null, FormatType.NULL)));
        }
    }

    public void setBoolean(String parameter, boolean x) throws SQLException {
        for (Integer i : getParameterIndexes(parameter)) {
            getPreparedStatement().setBoolean(i, x);
            this.equivalentSQL = this.equivalentSQL.replaceFirst("(?i):" + parameter, Matcher.quoteReplacement(format((Boolean) x, FormatType.BOOLEAN)));
        }
    }

    public void setByte(String parameter, byte x) throws SQLException {
        for (Integer i : getParameterIndexes(parameter)) {
            getPreparedStatement().setByte(i, x);
            this.equivalentSQL = this.equivalentSQL.replaceFirst("(?i):" + parameter, Matcher.quoteReplacement(format((Byte) x, FormatType.BYTE)));
        }
    }

    public void setShort(String parameter, short x) throws SQLException {
        for (Integer i : getParameterIndexes(parameter)) {
            getPreparedStatement().setShort(i, x);
            this.equivalentSQL = this.equivalentSQL.replaceFirst("(?i):" + parameter, Matcher.quoteReplacement(format((Short) x, FormatType.SHORT)));
        }
    }

    public void setInt(String parameter, int x) throws SQLException {
        for (Integer i : getParameterIndexes(parameter)) {
            getPreparedStatement().setInt(i, x);
            this.equivalentSQL = this.equivalentSQL.replaceFirst("(?i):" + parameter, Matcher.quoteReplacement(format((Integer) x, FormatType.INTEGER)));
        }
    }

    public void setLong(String parameter, long x) throws SQLException {
        for (Integer i : getParameterIndexes(parameter)) {
            getPreparedStatement().setLong(i, x);
            this.equivalentSQL = this.equivalentSQL.replaceFirst("(?i):" + parameter, Matcher.quoteReplacement(format((Long) x, FormatType.LONG)));
        }
    }

    public void setFloat(String parameter, float x) throws SQLException {
        for (Integer i : getParameterIndexes(parameter)) {
            getPreparedStatement().setFloat(i, x);
            this.equivalentSQL = this.equivalentSQL.replaceFirst("(?i):" + parameter, Matcher.quoteReplacement(format((Float) x, FormatType.FLOAT)));
        }
    }

    public void setDouble(String parameter, double x) throws SQLException {
        for (Integer i : getParameterIndexes(parameter)) {
            getPreparedStatement().setDouble(i, x);
            this.equivalentSQL = this.equivalentSQL.replaceFirst("(?i):" + parameter, Matcher.quoteReplacement(format((Double) x, FormatType.DOUBLE)));
        }
    }

    public void setBigDecimal(String parameter, BigDecimal x) throws SQLException {
        for (Integer i : getParameterIndexes(parameter)) {
            getPreparedStatement().setBigDecimal(i, x);
            this.equivalentSQL = this.equivalentSQL.replaceFirst("(?i):" + parameter, Matcher.quoteReplacement(format((BigDecimal) x, FormatType.BIGDECIMAL)));
        }
    }

    public void setString(String parameter, String x) throws SQLException {
        for (Integer i : getParameterIndexes(parameter)) {
            getPreparedStatement().setString(i, x);
            this.equivalentSQL = this.equivalentSQL.replaceFirst("(?i):" + parameter, Matcher.quoteReplacement(format((String) x, FormatType.STRING)));
        }
    }

    public void setBytes(String parameter, byte[] x) throws SQLException {
        for (Integer i : getParameterIndexes(parameter)) {
            getPreparedStatement().setBytes(i, x);
            String fval = "";
            for (int j = 0; j < x.length; j++) {
                fval += (char) x[j] + ",";
            }
            if (fval.endsWith(",")) {
                fval = fval.substring(0, fval.length() - 1);
            }
            this.equivalentSQL = this.equivalentSQL.replaceFirst("(?i):" + parameter, Matcher.quoteReplacement(format((String) fval, FormatType.STRING)));
        }
    }

    public void setDate(String parameter, Date x) throws SQLException {
        for (Integer i : getParameterIndexes(parameter)) {
            getPreparedStatement().setDate(i, x);
            this.equivalentSQL = this.equivalentSQL.replaceFirst("(?i):" + parameter, Matcher.quoteReplacement(format((Date) x, FormatType.DATE)));
        }
    }

    public void setTime(String parameter, Time x) throws SQLException {
        for (Integer i : getParameterIndexes(parameter)) {
            getPreparedStatement().setTime(i, x);
            this.equivalentSQL = this.equivalentSQL.replaceFirst("(?i):" + parameter, Matcher.quoteReplacement(format((Time) x, FormatType.TIME)));
        }
    }

    public void setTimestamp(String parameter, Timestamp x) throws SQLException {
        for (Integer i : getParameterIndexes(parameter)) {
            getPreparedStatement().setTimestamp(i, x);
            this.equivalentSQL = this.equivalentSQL.replaceFirst("(?i):" + parameter, Matcher.quoteReplacement(format((Timestamp) x, FormatType.TIMESTAMP)));
        }
    }

    public String getQuery() {
        return this.equivalentSQL.trim();
    }

    private String format(Object o, FormatType type) {
        String returnParam = "";
        try {
            switch (type) {
                case NULL:
                    returnParam = "NULL";
                    break;
                case BIGDECIMAL:
                    returnParam = ((o == null) ? "NULL" : "'" + ((BigDecimal) o).toString() + "'");
                    break;
                case BOOLEAN:
                    returnParam = ((o == null) ? "NULL" : "'" + (((Boolean) o == Boolean.TRUE) ? "1" : "0") + "'");
                    break;
                case BYTE:
                    returnParam = ((o == null) ? "NULL" : "'" + ((Byte) o).intValue() + "'");
                    break;
                case DATE:
                    returnParam = ((o == null) ? "NULL" : "'" + new SimpleDateFormat("yyyy-MM-dd").format((Date) o) + "'");
                    break;
                case DOUBLE:
                    returnParam = ((o == null) ? "NULL" : "'" + ((Double) o).toString() + "'");
                    break;
                case FLOAT:
                    returnParam = ((o == null) ? "NULL" : "'" + ((Float) o).toString() + "'");
                    break;
                case INTEGER:
                    returnParam = ((o == null) ? "NULL" : ((Integer) o).toString());
                    break;
                case LONG:
                    returnParam = ((o == null) ? "NULL" : "'" + ((Long) o).toString() + "'");
                    break;
                case SHORT:
                    returnParam = ((o == null) ? "NULL" : "'" + ((Short) o).toString() + "'");
                    break;
                case STRING:
                    returnParam = ((o == null) ? "NULL" : "'" + o.toString() + "'");
                    break;
                case STRINGLIST:
                    returnParam = ((o == null) ? "NULL" : "'" + o.toString() + "'");
                    break;
                case TIME:
                    returnParam = ((o == null) ? "NULL" : "'" + new SimpleDateFormat("hh:mm:ss a").format(o) + "'");
                    break;
                case TIMESTAMP:
                    returnParam = ((o == null) ? "NULL" : "'" + new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a").format(o) + "'");
                    break;
                default:
                    break;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return returnParam.trim();
    }
}