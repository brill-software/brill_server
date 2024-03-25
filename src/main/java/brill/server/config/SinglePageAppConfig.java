// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT License.
package brill.server.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Redirects every page to index.html so that the Router can handle requests.
 * 
 * This class is required to ensure that when a user enters a URL in the web
 * browser the request gets redirected to /index.html and the React App Router.
 * Without this class, the user would get a "White label error page" on entering
 * a URL.
 * 
 */
@Configuration
public class SinglePageAppConfig implements WebMvcConfigurer {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SinglePageAppConfig.class);

    @Autowired
    private BotsConfig botsConfig;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/").resourceChain(false)
                .addResolver(new PushStateResourceResolver());
    }
    private class PushStateResourceResolver implements ResourceResolver {
        
        private Resource index = new ClassPathResource("/static/index.html");
        private List<String> handledExtensions = Arrays.asList("html", "js", "json", "csv", "css", "png", "svg", "eot",
                "ttf", "woff", "appcache", "jpg", "jpeg", "gif", "ico");
        private List<String> ignoredPaths = Arrays.asList("brill_ws","brill_socksjs");


        @Override
        public Resource resolveResource(@Nullable HttpServletRequest request, String requestPath,
                List<? extends Resource> locations, ResourceResolverChain chain) {
            if (request != null && botsConfig.isRequestFromBot(request)) {
                 return botsConfig.resolveBotRequest(requestPath, locations);
            }
            return resolve(requestPath, locations);
        }

        @Override
        public String resolveUrlPath(String resourcePath, List<? extends Resource> locations,
                ResourceResolverChain chain) {
            Resource resolvedResource = resolve(resourcePath, locations);
            if (resolvedResource == null) {
                return null;
            }
            try {
                return resolvedResource.getURL().toString();
            } catch (IOException e) {
                return resolvedResource.getFilename();
            }
        }

        private Resource resolve(String requestPath, List<? extends Resource> locations) {
            if (isIgnored(requestPath)) {
                return null;
            }
            if (isHandled(requestPath)) {
                try {
                    return locations.stream().map(loc -> createRelative(loc, requestPath))
                            .filter(resource -> resource != null && resource.exists()).findFirst().orElseGet(null);
                } catch (NullPointerException npe) {
                    log.error("NPE processing request path " + requestPath);
                }
            }
            return index;
        }

        private Resource createRelative(Resource resource, String relativePath) {
            try {
                return resource.createRelative(relativePath);
            } catch (IOException e) {
                return null;
            }
        }

        private boolean isIgnored(String path) {
            return ignoredPaths.contains(path);
        }

        private boolean isHandled(String path) {
            String extension = StringUtils.getFilenameExtension(path);
            return handledExtensions.stream().anyMatch(ext -> ext.equals(extension));
        }
    }
}
