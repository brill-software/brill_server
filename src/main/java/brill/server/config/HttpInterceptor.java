// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT license.
package brill.server.config;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor that redirects http requests to https.
 * 
 * Added by the WebMvcConfig class when server.redirectHttpToHttps is set to true in application.yaml.
 * 
 */
@Component
public class HttpInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("http".equals(request.getScheme())) {
            response.sendRedirect("https://" + request.getServerName() + request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : ""));
            return false;
        }
        return true;        
    } 
}