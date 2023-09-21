// © 2021 Brill Software Limited - Brill Framework, distributed under the Brill Software Proprietry License.
package brill.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WebMvcConfigurer.class);

    @Value("${server.redirectHttpToHttps:false}")
    private Boolean redirectHttpToHttps;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (redirectHttpToHttps) {
            log.info("Adding interceptor to redirect http requests to https");
            registry.addInterceptor(new HttpInterceptor());
        }  
    }
}