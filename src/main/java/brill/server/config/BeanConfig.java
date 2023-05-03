// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT license.
package brill.server.config;

import brill.server.database.Database;
import brill.server.git.GitRepository;
import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import static brill.server.git.GitRepository.*;
import static java.lang.String.format;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

@Configuration
public class BeanConfig {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BeanConfig.class);

    @Autowired
    private Environment environment;

    @Value("${brill.apps.repo:}")
    String remoteRepositoryUrl;

    @Value("${brill.apps.local.repo.dir:}")
    String localRepoDir;

    @Value("${brill.apps.local.repo.skip.pull:false}")
    Boolean skipRepoPull;

    @Value("${server.extraHttpPort:-1}")
    private Integer extraPort;

    @Value("${database.driver:}")
    String driver;

    @Value("${database.url:}")
    String url;

    @Value("${database.username:}")
    String username;

    @Value("${database.password:}")
    String password;

    /**
     * Creates a bean at startup for accessing the apps config repository. Either
     * clones the repo if it doesn't already exist or does a pull.
     * 
     * @return GitService bean
     */
    @Bean("gitAppsRepo")
    public GitRepository gitAppsRepoBean() {
        try {
            String[] profiles = environment.getActiveProfiles();
            if (profiles.length == 0) {
                throw new UnsupportedOperationException(
                        "**** There's no spring boot profile set. Please set up a profile ****");
            }

            if (remoteRepositoryUrl.length() == 0 || localRepoDir.length() == 0) {
                throw new UnsupportedOperationException(
                        "**** Properties 'brill.apps.repo' and 'brill.local.repo.dir' must be setup for the profile in application.yml ****");
            }

            log.info(format("Remote Repo: %s", remoteRepositoryUrl));
            log.info(format("Local Repro: %s", localRepoDir));

            GitRepository repo = new GitRepository(remoteRepositoryUrl, localRepoDir);

            if (repo.localRepoExits(PRODUCTION_WORKSPACE)) {
                if (!skipRepoPull) {
                    repo.pull(PRODUCTION_WORKSPACE, MASTER);
                }
            } else {
                repo.deleteLocalRepo(PRODUCTION_WORKSPACE);
                repo.cloneRemoteRepository(PRODUCTION_WORKSPACE, MASTER);
            }

            if (repo.localRepoExits(DEVELOPMENT_WORKSPACE)) {
                if (!skipRepoPull) {
                    repo.pull(DEVELOPMENT_WORKSPACE, DEVELOP);
                }
            } else {
                repo.deleteLocalRepo(DEVELOPMENT_WORKSPACE);
                try {
                    repo.cloneRemoteRepository(DEVELOPMENT_WORKSPACE, DEVELOP);
                } catch (Exception e) {
                    log.error("**** Unable to get the develop branch from the repository: ", e.getMessage());
                    // Carry on with just the master branch.
                }
                
            }

            return repo;

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            System.exit(1);
            return null;
        }
    }

    @Bean("database")
    public Database databaseServiceBean() {
        return new Database(driver, url, username, password);
    }
    
    /**
     * Connects the server to an additional http port configured using the 'server.extraHttpPort' property
     * in application.yaml .
     * 
     * This is useful in Production to either have part of the website using http and the other part https or
     * all http requests can be forwarded to https by also using the 'server.redirectHttpToHttps' property.
     * 
     * The standard config in a Production environment is to accept https requests on port 433 and redirect any
     * http requests on port 80 to https port 433.
     * 
     */
    @Bean
    public TomcatServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory() {};
        if (this.extraPort > 0 && this.extraPort <= 65535) {
            Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
            connector.setScheme("http");
            connector.setPort(this.extraPort);
            tomcat.addAdditionalTomcatConnectors(connector);
        }
        return tomcat;
    }

    @Bean
    public Filter myFilter() {
        return new Filter() {
            @Override
            public void init(FilterConfig filterConfig) throws ServletException {
            }
    
            @Override
            public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
                final HttpServletResponse res = (HttpServletResponse) servletResponse;
                res.addHeader("X-Powered-By", "Brill CMS");
                filterChain.doFilter(servletRequest, servletResponse);
            }
    
            @Override
            public void destroy() {
            }
        };
    }
}