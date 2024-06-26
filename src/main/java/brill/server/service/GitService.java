// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT License.
package brill.server.service;

import brill.server.domain.Subscriber;
import brill.server.exception.GitServiceException;
import brill.server.exception.WebSocketException;
import brill.server.git.GitRepository;
import brill.server.utils.JsonUtils;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.json.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import static java.lang.String.format;

/**
 * Git Services - supports access to the git repository. 
 * 
 */
@Service
public class GitService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GitService.class);

    @Value("${media.library.shared.dir:}")
    String mediaLibrarySharedDir;

    @Autowired
    @Qualifier("gitAppsRepo")
    GitRepository gitRepo;

    @Autowired
    private WebSocketService wsService;

    public boolean doesWorkspaceAlreadyExist(String workspace) {
        return gitRepo.doesWorkspaceAlreadyExist(workspace);
    }

    /**
     * Deletes any existing workspace files, creates a new workspace and clones the repository.
     * 
     * @param newWorkspace
     * @param branch
     * @throws GitServiceException
     */
    public void createNewWorkspace(String repository, String newWorkspace, String branch) throws GitServiceException {
        gitRepo.deleteLocalRepo(newWorkspace);
        gitRepo.cloneRemoteRepository(repository, newWorkspace, branch);
        this.createMediaLibrarySharedLink(newWorkspace);  
    }

    public void createNewWorkspace(String newWorkspace, String branch) throws GitServiceException {
        gitRepo.deleteLocalRepo(newWorkspace);
        gitRepo.cloneRemoteRepository(newWorkspace, branch);
        this.createMediaLibrarySharedLink(newWorkspace);
    }

    public JsonObject getFileTree(String workspace, String topic, JsonObject filter) throws GitServiceException {
        boolean includeFileContent = false;
        List<String> hiddenApps = new ArrayList<String>();
        if (filter != null) {    
            if (filter.containsKey("includeFileContent") &&
                filter.get("includeFileContent").getValueType().name().equals("TRUE")) {
                    includeFileContent = true;
                }
            if (filter.containsKey("hiddenApps")) {
                hiddenApps =  Arrays.asList(filter.getString("hiddenApps").split(","));
            }
        }

        return gitRepo.getFileTree(workspace, getPath(topic), includeFileContent, hiddenApps);
    }

    public JsonObject getFileTree(String workspace, String topic) throws GitServiceException {
        return gitRepo.getFileTree(workspace, getPath(topic), false, null);
    }

    /**
     * Saves a tree of directories and files. Used by the CMS Paste command.
     * 
     * @param workspace
     * @param topic
     * @param content
     * @throws GitServiceException
     */
    public void saveFileTree(String workspace, String topic, JsonObject content) throws GitServiceException {
        gitRepo.saveFileTree(workspace, getPath(topic), content);

        // Notify any clients that have a git:status:/ subscription
        publishGitStatus(workspace);

        // Notify anyone that has a subscription to file:/ of the revised file tree after deleting the file.
        publishTopicTree(workspace);
    }


    public String getFile(String workspace, String topic) throws GitServiceException {
        return gitRepo.getFile(workspace, getPath(topic));
    }

    public byte[] getBinaryFile(String workspace, String topic) throws GitServiceException {
        return gitRepo.getBinaryFile(workspace, getPath(topic));
    }

    public String getFileBase64Encoded(String workspace, String topic) throws GitServiceException {
        return gitRepo.getFileBase64Encoded(workspace, getPath(topic));
    }

    public void saveFile(String workspace, String topic, String content, boolean contentBase64Encoded, boolean noOverwrite) throws GitServiceException {
        byte[] contentBytes = contentBase64Encoded ? Base64.getDecoder().decode(content) : content.getBytes();
        gitRepo.createOrUpdateFile(workspace, getPath(topic), contentBytes, noOverwrite);

        // Notify any clients that have a git:status:/ subscription
        publishGitStatus(workspace);

        // Notify anyone that has a subscription to file:/ of the revised file tree after adding the file.
        publishTopicTree(workspace);
    }

    public void saveFile(String workspace, String topic, String content, boolean contentBase64Encoded) throws GitServiceException {
        saveFile(workspace, topic, content, contentBase64Encoded, false);
    }

    public List<String> getBranchList(String workspace, boolean excludeCurrentBranch) throws GitServiceException {
        return gitRepo.getBranchList(workspace, excludeCurrentBranch);
    }

    public List<String> getRepoBranchList(String workspace) throws GitServiceException {
        return gitRepo.getRepoBranchList(workspace);
    }

    public String getCurrentBranch(String workspace) throws GitServiceException {
        return gitRepo.getCurrentBranch(workspace);
    }

    public String createNewBranch(String workspace, String branchOffOf, String newBranch) throws GitServiceException {
        return gitRepo.createNewBranch(workspace, branchOffOf, newBranch);
    }

    public void switchBranch(String workspace, String newBranch) throws GitServiceException {
        gitRepo.switchBranch(workspace, newBranch); 
        this.createMediaLibrarySharedLink(workspace); 
    }

    public void mergeBranch(String workspace, String branch) throws GitServiceException {
        gitRepo.mergeBranch(workspace, branch);
    }

    public void deleteBranch(String workspace, String branch, boolean deleteFromRepo, boolean forceDelete) throws GitServiceException {
        gitRepo.deleteBranch(workspace, branch, deleteFromRepo, forceDelete);   
    }  

    public void checkoutBranch(String workspace, String branch) throws GitServiceException {
        gitRepo.checkoutBranch(workspace, branch);


    } 

    public JsonObject getCommitsForBranchOrFile(String workspace, String branch, String fileName) throws GitServiceException {
        return gitRepo.getCommitsForBranchOrFile(workspace, branch, fileName);
    }

    public JsonObject getLog(String workspace, String branch, boolean merge, boolean rebase, boolean pull) throws GitServiceException {
        return gitRepo.getLog(workspace, branch, merge, rebase, pull);
    }

    public String getTrackingBranch(String workspace) throws GitServiceException {
        return gitRepo.getTrackingBranch(workspace);
    }

    public String getRemoteRepo(String workspace) throws GitServiceException {
        return gitRepo.getRemoteRepo(workspace);
    }

    public void publishGitStatus(String workspace) throws GitServiceException {
        // Publish the change to everyone subscribed to git:status:/
        List<Subscriber> subscribers = wsService.getSubscribers("git:status:/");
        String content = getStatus(workspace).toString();
        for (Subscriber subscriber : subscribers) {
            try {
                wsService.sendMessageToClient(subscriber.getSession(), "publish", "git:status:/", content);
            } catch (WebSocketException e) {
                log.error("Unable to updte client with git:status:/");
            }
        }
    }

    public void saveFile(String workspace, String topic, String content) throws GitServiceException {
        saveFile(workspace, getPath(topic), content, false);
    }

    public void newFile(String workspace, String topic) throws GitServiceException {
       
        gitRepo.newFile(workspace, getPath(topic));

        // Notify any clients that have a git:status:/ subscription
        publishGitStatus(workspace);

        // Notify anyone that has a subscription to file:/ of the revised file tree after adding the file.
        publishTopicTree(workspace);
    }

    public void newFolder(String workspace, String topic) throws GitServiceException {
       
        gitRepo.newFolder(workspace, getPath(topic));

        // Notify any clients that have a git:status:/ subscription
        publishGitStatus(workspace);

        // Notify anyone that has a subscription to file:/ of the revised file tree after adding the file.
        publishTopicTree(workspace);
    }

    public void deleteFileOrDirectory(String workspace, String topic) throws GitServiceException {
       
        gitRepo.deleteFileOrDirectory(workspace, getPath(topic));

        // Notify any clients that have a git:status:/ subscription
        publishGitStatus(workspace);

        // Notify anyone that has a subscription to file:/ of the revised file tree after deleting the file.
        publishTopicTree(workspace);
    }

    public void moveFile(String workspace, String topic, String newPath) throws GitServiceException {
       
        gitRepo.moveFile(workspace, getPath(topic), newPath);

        // Notify any clients that have a git:status:/ subscription
        publishGitStatus(workspace);

        // Notify anyone that has a subscription to file:/ of the revised file tree after deleting the file.
        publishTopicTree(workspace);
    }

    public void duplicateFile(String branch, String topic) throws GitServiceException {
       
        gitRepo.duplicateFile(branch, getPath(topic));

        // Notify any clients that have a git:status:/ subscription
        publishGitStatus(branch);

        // Notify anyone that has a subscription to file:/ of the revised file tree.
        publishTopicTree(branch);
    }

    public void duplicateFolder(String workspace, String topic) throws GitServiceException {
       
        gitRepo.duplicateFolder(workspace, getPath(topic));

        // Notify any clients that have a git:status:/ subscription
        publishGitStatus(workspace);

        // Notify anyone that has a subscription to file:/ of the revised file tree.
        publishTopicTree(workspace);
    }

    public void refresh(String workspace) throws GitServiceException {
        publishGitStatus(workspace);
        publishTopicTree(workspace);
    }

    private void publishTopicTree(String workspace) throws GitServiceException{
        String topic = "file:/";
        //String content = getFileTree(workspace, topic).toString();
        List<Subscriber> subscribers = wsService.getSubscribers(topic);
        for (Subscriber subscriber : subscribers) {
            if (wsService.getWorkspace(subscriber.getSession()).equals(workspace)) {
                try {
                    JsonObject filter = JsonUtils.jsonFromString(subscriber.getFilter());
                    String content = getFileTree(workspace, topic, filter).toString();
                    wsService.sendMessageToClient(subscriber.getSession(), "publish", topic, content);
                } catch (WebSocketException e) {
                    // Ignore
                }
            }
        }
    }

    public JsonObject getStatus(String workspace) throws GitServiceException {
        return gitRepo.status(workspace);
    }

    public void addFile(String workspace, String file) throws GitServiceException {
        gitRepo.add(workspace, file);
        publishGitStatus(workspace);
    }

    public void rmFile(String workspace, String file) throws GitServiceException {
        gitRepo.rm(workspace, file);
        publishGitStatus(workspace);
    }

    public void restore(String workspace, String file) throws GitServiceException {
        gitRepo.restore(workspace, file);
        publishGitStatus(workspace);
    }


    public void unstageFile(String workspace, String file) throws GitServiceException {
        gitRepo.unstage(workspace, file);
        publishGitStatus(workspace);
    }

    public void commitStagedFiles(String workspace, String message, String authorName, String authorEmail) throws GitServiceException {
        gitRepo.commit(workspace, message, authorName, authorEmail);
        publishGitStatus(workspace);
    }

    public void stash(String workspace, String message) throws GitServiceException {
        gitRepo.stash(workspace, message);
        publishGitStatus(workspace);
         // Notify anyone that has a subscription to file:/ of the revised file tree.
         publishTopicTree(workspace);
    }

    public void stashPop(String workspace, String stashRef) throws GitServiceException {
        gitRepo.stashPop(workspace, stashRef);
        publishGitStatus(workspace);
         // Notify anyone that has a subscription to file:/ of the revised file tree.
         publishTopicTree(workspace);
    }

    public void stashDrop(String workspace, String stashRef) throws GitServiceException {
        gitRepo.stashDrop(workspace, stashRef);
        publishGitStatus(workspace);
    }

    public void pull(String workspace, String branch) throws GitServiceException {
        // Pull any changes for the branch.
        ArrayList<String> fileList = gitRepo.pull(workspace, branch);

        // Send any pulled files to any clients that have subscribed.
        for (String filePath : fileList) {
            String fileContent = gitRepo.getFile(workspace, "/" + filePath);
            String topic = "file:/" + filePath;
            List<Subscriber> subscribers = wsService.getSubscribers(topic);
            for (Subscriber subscriber : subscribers) {
                try {
                    wsService.sendMessageToClient(subscriber.getSession(), "publish", topic, fileContent, true);
                } catch (WebSocketException e) {
                    // Ignore
                }
            }
            if (filePath.endsWith(".json")) {
                topic = "json:/" + filePath;
                subscribers = wsService.getSubscribers(topic);
                for (Subscriber subscriber : subscribers) {
                    try {
                        wsService.sendMessageToClient(subscriber.getSession(), "publish", topic, fileContent, false, true);
                    } catch (WebSocketException e) {
                        // Ignore
                    }
                }
            }
        }
    }

    public void rebase(String workspace, String branch) throws GitServiceException {
        // Pull any changes for the branch.
        ArrayList<String> fileList = gitRepo.rebase(workspace, branch);

        // Send any pulled files to any clients that have subscribed.
        for (String filePath : fileList) {
            String fileContent = gitRepo.getFile(workspace, "/" + filePath);
            String topic = "file:/" + filePath;
            List<Subscriber> subscribers = wsService.getSubscribers(topic);
            for (Subscriber subscriber : subscribers) {
                try {
                    wsService.sendMessageToClient(subscriber.getSession(), "publish", topic, fileContent, true);
                } catch (WebSocketException e) {
                    // Ignore
                }
            }
            if (filePath.endsWith(".json")) {
                topic = "json:/" + filePath;
                subscribers = wsService.getSubscribers(topic);
                for (Subscriber subscriber : subscribers) {
                    try {
                        wsService.sendMessageToClient(subscriber.getSession(), "publish", topic, fileContent, false, true);
                    } catch (WebSocketException e) {
                        // Ignore
                    }
                }
            }
        }
    }

    public String getLastCommittedFile(String workspace, String filePath) throws GitServiceException {
        return gitRepo.getLastCommittedFile(workspace, filePath);
    }

    private void createMediaLibrarySharedLink(String workspace) {
        try {
            gitRepo.createSymbolicLink(workspace, "MediaLibrary/shared", mediaLibrarySharedDir);
        } catch (GitServiceException e) {
            log.error("Unable to create shared link in Media Library folder: " + e.getMessage());
        }   
    }


    private String getPath(String topic) throws GitServiceException {
        try {
            URI topicUri = new URI(topic);
            return topicUri.getPath();
        } catch (URISyntaxException e) {
            throw new GitServiceException(format("Unable to get path from topic %s : %s", topic, e.getMessage()));
        }
    }
}