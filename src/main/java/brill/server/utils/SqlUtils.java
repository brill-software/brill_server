// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT license.
package brill.server.utils;

public class SqlUtils {

    /**
     * Strips comments from an SQL string. The comment types supported are:
     * 1) Single or Multi-line using slash star and star slash
     * 2) Single line using -- and terminated by a newline charater
     * 3) Single line using # and terminated by a newline charater
     * 
     * Comment sequences within single or double quotes are not stripped.
     * Blank lines are stripped.
     * 
     * @param sql
     * @return
     */
    public static String stripComments(String sql) {
        int length = sql.length();
        StringBuffer result = new StringBuffer(length);
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inSingleLineComment = false;
        boolean inMultiLineComment = false;
        char lastC = '\n';

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
            } else if (inMultiLineComment) {
                if (c == '*' && sql.charAt(i + 1) == '/') {
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
            } else if (c == '/' && sql.charAt(i + 1) == '*') {
                i++;
                inMultiLineComment = true;
                continue;
            } else if (c == '-' && sql.charAt(i + 1) == '-' || c == '#') {
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