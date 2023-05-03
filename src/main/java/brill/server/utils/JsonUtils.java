// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT license.
package brill.server.utils;

import java.io.StringReader;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import brill.server.exception.MissingValueException;
import static java.lang.String.format;

public class JsonUtils {

    /**
     * Same as calling message.getJsonObject() but also checks that the key exists and throws an excpetion if it doesn't.
     * 
     * @param message
     * @param key
     * @return
     * @throws MissingValueException
     */
    public static JsonObject getJsonObject(JsonObject message, String key) throws MissingValueException {
        if (!message.containsKey(key)) {
            throw new MissingValueException(format("Message is missing a '%s' field.", key));
        }
        return message.getJsonObject(key);
    }

    /**
     * The same as calling jsonObj.getString() but also checks that the key exists and that the value is of type STRING
     * 
     * @param message
     * @param key
     * @return
     * @throws MissingValueException
     */
    public static String getString(JsonObject jsonObj, String key) throws MissingValueException {
        if (!jsonObj.containsKey(key)) {
            throw new MissingValueException(format("Message is missing a '%s' string.", key));
        }
        String jsonType = jsonObj.get(key).getValueType().name();
        if (!jsonType.equals("STRING")) {
            throw new MissingValueException(format("The '%s' field contains a %s. It should contain a STRING.", key, jsonType));
        }
        return jsonObj.getString(key);
    }

    /**
     * The same as calling jsonObj.getString() but also checks that the key exists and that the value is of type STRING
     * 
     * @param message
     * @param key
     * @return
     * @throws MissingValueException
     */
    public static boolean getBoolean(JsonObject jsonObj, String key) throws MissingValueException {
        if (!jsonObj.containsKey(key)) {
            throw new MissingValueException(format("Message is missing a '%s' boolean.", key));
        }
        String jsonType = jsonObj.get(key).getValueType().name();
        if (!jsonType.equals("FALSE") && !jsonType.equals("TRUE")) {
            throw new MissingValueException(format("The '%s' field contains a %s. It should contain a BOOLEAN.", key, jsonType));
        }
        return jsonObj.getBoolean(key);
    }



    
    public static JsonObject jsonFromString(String jsonObjectStr) {

        JsonReader jsonReader = Json.createReader(new StringReader(jsonObjectStr));
        JsonObject object = jsonReader.readObject();
        jsonReader.close();
        return object;
    }

    /**
     * 
     * 
     * @param jsonObjectStr
     * @return A JsonValue can be a boolean, object, array or string.
     */
    public static JsonValue jsonValueFromString(String jsonObjectStr) {
        JsonReader jsonReader = Json.createReader(new StringReader(jsonObjectStr));
        JsonValue value = jsonReader.readValue();
        jsonReader.close();
        return value;
    }

    /**
     * Adds an additional string field to an existing JsonObject. The JsonObject is
     * immutable and therefore a field can only be added creating a new JsonObject
     * and copying over the fields plus the new field and value.
     */
    public static JsonObject add(JsonObject original, String field, String value){
        JsonObjectBuilder builder = Json.createObjectBuilder();
        for (Map.Entry<String,JsonValue> entry : original.entrySet()){
                builder.add(entry.getKey(), entry.getValue());
        }
        builder.add(field, value); 
        return builder.build();
    }
    
    /**
     * Strips comments from an a .jsonc string. The comment types supported are:
     * 1) Single or Multi-line using slash star and star slash
     * 2) Single line using // and terminated by a newline charater
     * 
     * Comment sequences within single or double quotes are not stripped.
     * Blank lines are stripped.
     * 
     * @param sql
     * @return
     */
    public static String stripComments(String jsonc) {
        int length = jsonc.length();
        StringBuffer result = new StringBuffer(length);
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inSingleLineComment = false;
        boolean inMultiLineComment = false;
        char lastC = '\n';

        for (int i = 0; i < length; i++) {
            char c = jsonc.charAt(i);
            if (inSingleQuote) {
                if (c == '\'') {
                    inSingleQuote = false;
                }
            } else if (inDoubleQuote) {
                if (c == '"') {
                    inDoubleQuote = false;
                }
            } else if (inMultiLineComment) {
                if (c == '*' && jsonc.charAt(i + 1) == '/') {
                    inMultiLineComment = false;
                    i++;
                }
                continue;
            } else if (inSingleLineComment) {
                if (c == '\n') {
                    inSingleLineComment = false;
                } else {
                    continue;
                }            
            } else if (c == '\'') {
                inSingleQuote = true;
            } else if (c == '"') {
                inDoubleQuote = true;
            } else if (c == '/' && jsonc.charAt(i + 1) == '*') {
                i++;
                inMultiLineComment = true;
                continue;
            } else if (c == '/' && jsonc.charAt(i + 1) == '/') {
                inSingleLineComment = true;
                continue;
            }

            if (lastC == '\n' && c == '\n') {
                continue;
            }
            result.append(c);
            lastC = c;
        }
        return result.toString();
    } 
}