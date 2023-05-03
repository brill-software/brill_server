package brill.server.git;

import brill.server.exception.GitServiceException;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import static brill.server.git.GitRepository.*;
import static java.lang.String.format;

@RunWith(JUnitPlatform.class)
@ExtendWith(MockitoExtension.class)
class GitRepositoryTest {

    static final Level LOG_LEVEL = Level.INFO;

    @BeforeEach
    void setUp() {
        final Logger logger = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.setLevel(LOG_LEVEL);
    }

    @Test
    public void testOfTraceLevelLogging() { 
        System.out.println("Trace level logging is on.");
        System.out.println("Finished");
    }

    @Test
    public void cloneOfRepository_InvalidRepo() {
        System.out.println("Testing clone of invalid repository");
        GitRepository repo = new GitRepository("XXXXXX", "/Users/chrisbulcock/BrillAppTestRepositories");
        assertThrows(GitServiceException.class,
            () -> {
                      repo.cloneRemoteRepository("develop", DEVELOP);
            });
    }

    @Disabled
    @Test
    public void cloneOfRepository() throws GitServiceException {
        System.out.println("Testing clone of repository");
        GitRepository repo = new GitRepository("git@bitbucket.org:brillsoftware/brill_demo_app.git", 
            "/Users/chrisbulcock/BrillAppTestRepositories");

        // Delete repo if it already exists, before starting.
        if (repo.localRepoExits(DEVELOP)) {
            repo.deleteLocalRepo(DEVELOP);
        }

        // Clone repo
        repo.cloneRemoteRepository("develop", DEVELOP);
        boolean result = repo.localRepoExits(DEVELOP);
        assertTrue(result);

        // Do a Pull.
        //result = repo.pull(DEVELOP);
        //assertTrue(result);

        // Get pages.json file
        String json = repo.getFile(DEVELOP, "file:/pages.json");
        System.out.println(format("File: %s", json));
        assertTrue(json.length() > 0);

        // Delete local repo
        repo.deleteLocalRepo(DEVELOP);
        result = repo.localRepoExits(DEVELOP);
        assertFalse(result);

        System.out.println("Finished");
    }

    @Disabled
    @Test
    public void updateFileAndCommit() throws GitServiceException {
        System.out.println("Testing update and commit");
        GitRepository repo= new GitRepository("git@bitbucket.org:brillsoftware/brill_demo_app.git", 
            "/Users/chrisbulcock/BrillAppTestRepositories");

        // Delete repo if it already exists, before we start the tests.
        if (repo.localRepoExits(DEVELOP)) {
            repo.deleteLocalRepo(DEVELOP);
        }

        // Clone repo
        repo.cloneRemoteRepository("develop", DEVELOP);
        boolean result = repo.localRepoExits(DEVELOP);
        assertTrue(result);

        // Create/update file
        repo.createOrUpdateFile(DEVELOP, "createUpdateTest.json", "{ \"test\": \"abc\"}".getBytes());
        repo.stageCommitPushChange(DEVELOP, "demo", "createUpdateTest.json", "Test commit message");

        // Delete local repo
        repo.deleteLocalRepo(DEVELOP);
        result = repo.localRepoExits(DEVELOP);
        assertFalse(result);

        System.out.println("Finished");
    }


    @Disabled
    @Test
    public void mergeDevelopIntoMaster() throws GitServiceException {
        System.out.println("Testing merge");
        GitRepository repo= new GitRepository("git@bitbucket.org:brillsoftware/brill_demo_app.git", 
            "/Users/chrisbulcock/BrillAppTestRepositories");

        // Delete repo if it already exists, before we start the tests.
        if (repo.localRepoExits(MASTER)) {
            repo.deleteLocalRepo(MASTER);
        }
        if (repo.localRepoExits(DEVELOP)) {
            repo.deleteLocalRepo(DEVELOP);
        }

        // Clone a repo for 'master' and one for 'develop'.
        repo.cloneRemoteRepository("master", MASTER);
        boolean result = repo.localRepoExits(MASTER);
        assertTrue(result);
        repo.cloneRemoteRepository("develop", DEVELOP);
        result = repo.localRepoExits(DEVELOP);
        assertTrue(result);

        // Create/update file
        byte[] contentBytes = new String("{ \"test\": \"" + LocalDateTime.now().toString() + "\"}").getBytes();
        repo.createOrUpdateFile(DEVELOP, "createUpdateTest.json", contentBytes);
        repo.stageCommitPushChange(DEVELOP, "demo", "createUpdateTest.json", "Test commit message");

        // Merge develop into master
        result = repo.mergeDevelopIntoMaster(false);
        assertTrue(result);

        // Delete local repo
        repo.deleteLocalRepo(MASTER);
        result = repo.localRepoExits(MASTER);
        assertFalse(result);
        repo.deleteLocalRepo(DEVELOP);
        result = repo.localRepoExits(DEVELOP);
        assertFalse(result);

        System.out.println("Finished");
    }

    @Test
    public void tagRelease() throws GitServiceException {
        // GitRepository repo= new GitRepository("git@bitbucket.org:brillsoftware/brill_demo_app.git", 
        //     "/Users/chrisbulcock/BrillAppTestRepositories");
        
        // // Delete repo if it already exists, before we start the tests.
        // if (repo.localRepoExits(MASTER)) {
        //     repo.deleteLocalRepo(MASTER);
        // }

        // // Clone a repo for 'master' and one for 'develop'.
        // repo.cloneRemoteRepository("master", MASTER);
        // boolean result = repo.localRepoExits(MASTER);
        // assertTrue(result);

        // String releaseName = repo.tagMaster(false);
        // assertNotNull(releaseName);
        // System.out.println(format("Release: %s",releaseName));

        // // Delete local repo
        // repo.deleteLocalRepo(MASTER);
        // result = repo.localRepoExits(MASTER);
        // assertFalse(result);

        // System.out.println("Finished");
    }

    @Test
    public void listLocales() { 
        //returns array of all locales
        Locale locales[] = SimpleDateFormat.getAvailableLocales();
		
        //iterate through each locale and print 
        // locale code, display name and country
        for (int i = 0; i < locales.length; i++) {
            System.out.printf("%10s - %s, %s \n" , locales[i].toString(), 
                locales[i].getDisplayName(), locales[i].getDisplayCountry());
        }
    }
}
