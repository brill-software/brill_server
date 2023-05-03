// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT license.
package brill.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import brill.server.database.Database;
import brill.server.exception.JavaScriptException;
import brill.server.javaScriptHelper.Db;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import static java.lang.String.format;

/**
 * JavaScript Services - exectutes JavaScript using the Rhino JavaScript engine. 
 * 
 */
@Service
public class JavaScriptService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JavaScriptService.class);

    @Autowired
    @Qualifier("database")
    Database database;

    /**
     * Executes JavaScript using Rhino. Parameters are passed to the script using a Json string called "filter". The script is expected to
     * return the result as a Json string.
     * 
     * @param javaScript The ECMAScript 5.1 JavaScript.
     * @param filterJson A string contianing the filter Json.
     * @return A string containing Json.
     * @throws JavaScriptException
     */
    public String execute(String javaScript, String contentJson, String filterJson, String username) throws JavaScriptException {

        Context cx = Context.enter();
        
        try {
            // Fix to ensures strings returned by Java method calls are full JS strings.
            cx.getWrapFactory().setJavaPrimitiveWrap(false);

            Scriptable scope = cx.initStandardObjects();

            Object wrappedContentJson = Context.javaToJS(contentJson, scope);  
            ScriptableObject.putProperty(scope, "content", wrappedContentJson);

            Object wrappedFilterJson = Context.javaToJS(filterJson, scope);  
            ScriptableObject.putProperty(scope, "filter", wrappedFilterJson);

            Db.initialise(database); 
            Object wrappedDb = Context.javaToJS(new Db(), scope);
            ScriptableObject.putProperty(scope, "db", wrappedDb);

            Object wrappedUsername = Context.javaToJS(username, scope);
            ScriptableObject.putProperty(scope, "username", wrappedUsername);

            Object result = cx.evaluateString(scope, javaScript, "Script", 1, null);

            if (result == null) {
                return "{}";
            }
       
            String resultJson = Context.toString(result);
            return resultJson;

        } catch (EcmaError jsError) {
            log.error(format("JavaScript runtime error at line: %s : %s", jsError.lineNumber(),  jsError.getMessage()));
            throw new JavaScriptException(format("JavaScript error at line %n: %s",  jsError.lineNumber(),  jsError.getMessage()));
        }
        catch (org.mozilla.javascript.JavaScriptException jsEx) {
            log.error("*** Detail = " + jsEx.details());
            log.error("*** message = " + jsEx.getMessage()); 
            throw new JavaScriptException(format("Exception while running JavaScript: %s", jsEx.getMessage()));
        }
        catch (Exception e) {
            log.error(format("JavaScript error: %s", e.getMessage()));
            throw new JavaScriptException(format("Exception while running JavaScript: %s", e.getMessage()));
        }  
        finally {
            Context.exit();
        }
    }
}

/**
 * The commented out code below uses the Nashorn JavaScript engine, which was part of the Java JDK until Java 15 when it was removed. 
 * The Oracle suggested replacement is to use GraalVM but there are a lot of issues with GraalVM. GraalVM involves compiling the code 
 * to native code. On the AArch64 platform, GraalVM is maked as experimental and doesn't work on the Raspberry Pi for instance. 
 * Until GraalVM is available on all platforms, the fallback solution is to use the Rhino JavaScript engine. 
 */

// import jdk.nashorn.api.scripting.ClassFilter;
// import jdk.nashorn.api.scripting.NashornScriptEngineFactory;

// /**
//  * Exectutes JavaScript using the Nashorn JavaScript engine. Nashorn is marked as depreciated, so this code may
//  * need to be replaced in the future. Currently there are no alternatives that integrate with Java as well as Nashorn.
//  * 
//  * The JavaSctip can access any Java classes that are in the brill.server.javaScriptHelper package. Access to any other
//  * classes is blocked.
//  * 
//  */
// @Service
// @Slf4j
// public class JavaScriptService {

//     @Autowired
//     @Qualifier("database")
//     Database database;

//     @SuppressWarnings("deprecation")
//     class RestrictClassesAccess implements ClassFilter {
//         @Override
//         public boolean exposeToScripts(String s) {
//           if (s.startsWith("brill.server.javaScriptHelper")) {
//               return true;
//           }
//           log.error(format("JavaScript blocked from accessing class %s. See RestrictClassesAccess in JavaScriptService.", s));
//           return false;
//         }
//       }

//     /**
//      * Executes JavaScript. Parameters are passed to the script using a Json string called "request". The script is expected to
//      * return the result as a Json string.
//      * 
//      * @param javaScript The ECMAScript 5.1 JavaScript.
//      * @param requestJson A string contianing the filter Json.
//      * @return A string containing Json.
//      * @throws JavaScriptException
//      */
//     @SuppressWarnings("deprecation")
//     public String execute(String javaScript, String requestJson) throws JavaScriptException {
//         NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
//         ScriptEngine engine = factory.getScriptEngine(new JavaScriptService.RestrictClassesAccess());
//         try {
//             Bindings bindings = engine.createBindings();
//             Db.initialise(database);
//             bindings.put("filter", requestJson);
//             Object result = engine.eval(javaScript, bindings);
//             if (result == null) {
//                 return "{}";
//             }
//             return result.toString();
//         } catch (ScriptException e) {
//             throw new JavaScriptException(format("Exception while running JavaScript: %s", e.getMessage()));
//         }
//     }
// }