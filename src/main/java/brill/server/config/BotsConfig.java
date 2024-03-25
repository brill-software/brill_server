// © 2023 Brill Software Limited - Editerprise Edition, distributed under the Brill Software Enterprise Edition License.
package brill.server.config;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Enterprise Edition Module
 * 
 * Handles requests from  bots and web crawlers and redirects the requests to a static website setup under /static/bots.
 * To enable this functionality set "server.supportBotsWebsite" to true in the application.yaml .
 * 
 * It's difficult to get bots such as the Google Bot to correctly index a React Single Page app. The Google Bot 
 * supports JavaScript but not outbound connections using WebSockets or the fallbacks of xhr-streaming or xhr-polling. This
 * class is used to detect that it's a bot request and redirect the request onto a set of bots static HTML pages. The static
 * pages can contain text from the real pages and additional text and keywords to help with SEO.
 * 
 * The design might be changed in the future to dynamically create the pages, thus saving the need to create the static pages.
 * 
 */
@Service
public class BotsConfig {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BotsConfig.class);

    @Value("${server.botsWebsite:false}")
    private Boolean botsWebsite;

    public static String BOTS_WEBSITE = "/static/bots/";

    // The user agent string is searched to see if it matches any of the following string:
    private static final String[] BOTS = {"bot", "crawl", "spider", "slurp"};

    /**
    * Returns ture if the request is from a bot. The user agent is checked
    * agaist the BOTS list. The list needs to be short for performance reasons and
    * yet catch 95%+ of bots and not give any false positives.
    * For a list of bot and web crawler User Agents see:
    * https://perishablepress.com/list-all-user-agents-top-search-engines/
    *
    * @param request
    * @return True is the request is from a bot.
    */
    public boolean isRequestFromBot(HttpServletRequest request) {
        if (!botsWebsite) {
            return false;
        }

        String userAgent = request.getHeader("user-agent");
        if (userAgent == null) {
            return false;
        }
            
        String userAgentLC = userAgent.toLowerCase();
        for (String crawlerMatchStr : BOTS) {
            if (userAgentLC.contains(crawlerMatchStr)) {
                log.info("Web Crawler visit: User-Agent: " + userAgent);
                return true;
            }
        }
        return false;
    }

    /**
     * Takes a request path and maps it onto a resource file under resources/static/bots
     * 
     * @param requestPath
     * @param locations
     * @return
     */
    public Resource resolveBotRequest(String requestPath, List<? extends Resource> locations) {
        log.info("ResolveBotRequests requestPath = " + requestPath);
        String path = BOTS_WEBSITE + requestPath;

        if (StringUtils.getFilenameExtension(requestPath) == null) {
            path += "/index.html";
        }

        Resource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            log.error("Bot request error: requestPath = " + requestPath + " Can't find file " + path);
            return new ClassPathResource( BOTS_WEBSITE + "index.html");
        }

        return resource;
    }
}
