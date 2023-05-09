package brill.server.controller;
import javax.json.*;
import org.springframework.web.socket.WebSocketSession;
import brill.server.domain.Subscriber;
import brill.server.exception.GitServiceException;
import brill.server.exception.MissingValueException;
import brill.server.exception.WebSocketException;
import brill.server.service.*;
import brill.server.webSockets.annotations.*;
import brill.server.utils.JsonUtils;
import java.util.List;
import static brill.server.service.WebSocketService.*;
import static java.lang.String.format;

/**
 * Git Controller.
 */
@WebSocketController
public class GitController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GitController.class);

    private WebSocketService wsService;
    private GitService gitService;    

    public GitController(WebSocketService wsService, GitService gitService) {
        this.wsService = wsService;
        this.gitService = gitService;
    }

    /**
     * Sets the workspaces using request/response messaging.
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "request", topicMatches = "git:workspace:/", permission="cms_user")
    public void gitSetWorkspace(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            JsonObject content = JsonUtils.getJsonObject(message, "content");
            String newWorkspace = JsonUtils.getString(content, "workspace");
          
            String currentWorkspace = wsService.getWorkspace(session);

            if (!newWorkspace.equals(currentWorkspace)) {

                if (!gitService.doesWorkspaceAlreadyExist(newWorkspace)) {
                    wsService.sendErrorToClient(session, topic, "Creating Workspace", "Please wait while the workspace is created...", INFO_SEVERITY);
                    gitService.createNewWorkspace(newWorkspace, "master");
   
                    //See if we can also checkout the develop branch
                    if (!newWorkspace.equals("production")) {
           
                        try {
                            gitService.checkoutBranch(newWorkspace, "develop");
                        } catch (Exception e) {
                            log.error(format("Uanble to checkout develop branch to workspace %s", newWorkspace));
                        }
                    }  
                }
                
                wsService.setWorkspace(session, newWorkspace);
            }

            wsService.sendMessageToClient(session, "response", topic, "{}");

        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Workspace Error", e.getMessage() );
            log.error("Git set workspace exception: ", e.getMessage());
        }
    }    

   /**
     * Creates a new branch using request/response messaging and switches to the branch.
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "request", topicMatches = "git:newbranch:/", permission="cms_user")
    public void gitCreateNewBranch(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            JsonObject content = JsonUtils.getJsonObject(message, "content");
            String existingBranch = JsonUtils.getString(content, "existingBranch");
            String newBranch = JsonUtils.getString(content, "newBranch");
          
            String workspace = wsService.getWorkspace(session);

            gitService.createNewBranch(workspace, existingBranch, newBranch);

            wsService.sendMessageToClient(session, "response", topic, "{}");

        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Branch Creation Failed", e.getMessage() );
            log.error("Git set workspace exception: ", e);
        }
    }    

   /**
     * Sets the branch using request/response messaging.
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "request", topicMatches = "git:switchbranch:/", permission="cms_user")
    public void switchBranch(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            JsonObject content = JsonUtils.getJsonObject(message, "content");
            String newBranch = JsonUtils.getString(content, "newBranch");
          
            String workspace = wsService.getWorkspace(session);
            gitService.switchBranch(workspace, newBranch);

            wsService.sendMessageToClient(session, "response", topic, "{}");

        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Branch Switch Failed", e.getMessage() );
            log.error("Git set workspace exception: ", e);
        }
    }

   /**
     * Merges a branch into the current branch using request/response messaging.
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "request", topicMatches = "git:mergebranch:/", permission="cms_user")
    public void mergeBranch(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            JsonObject content = JsonUtils.getJsonObject(message, "content");
            String branch = JsonUtils.getString(content, "branch");
          
            String workspace = wsService.getWorkspace(session);
            gitService.mergeBranch(workspace, branch);

            wsService.sendMessageToClient(session, "response", topic, "{}");

        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Merge Failed:", e.getMessage() );
            log.error(format("Git merge branch exception: %s", e.getMessage()));
        }
    }

    /**
     * Deletes a branch from the workspace and from the repository.
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "request", topicMatches = "git:deletebranch:/", permission="cms_user")
    public void deleteBranch(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            JsonObject content = JsonUtils.getJsonObject(message, "content");
            String branch = JsonUtils.getString(content, "branch");
            boolean deleteFromRepo = JsonUtils.getBoolean(content, "deleteFromRepo");
            boolean forceDelete = JsonUtils.getBoolean(content, "forceDelete");
            String workspace = wsService.getWorkspace(session);

            gitService.deleteBranch(workspace, branch, deleteFromRepo, forceDelete);

            wsService.sendMessageToClient(session, "response", topic, "{}");
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Delete Failed", e.getMessage());
            log.error(format("Git delete branch exception: %s", e.getMessage()));
        }
    }

    /**
     * Checks out a branch to the workspace from the repository.
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "request", topicMatches = "git:checkoutbranch:/", permission="cms_user")
    public void checkoutBranch(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            JsonObject content = JsonUtils.getJsonObject(message, "content");
            String branch = JsonUtils.getString(content, "branch");
            String workspace = wsService.getWorkspace(session);

            gitService.checkoutBranch(workspace, branch);

            wsService.sendMessageToClient(session, "response", topic, "{}");
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Checkout Failed", e.getMessage());
            log.error(format("Git checkout branch exception: %s", e.getMessage()));
        }
    }

    /**
     * Restores a file and discards the edits.
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "request", topicMatches = "git:restore:/.+", permission="cms_user")
    public void restoreFile(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            String file = topic.substring("git:restore:/".length());
            String workspace = wsService.getWorkspace(session);
            gitService.restore(workspace, file);
            wsService.sendMessageToClient(session, "response", topic, "{}");
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Restoret Failed", e.getMessage());
            log.error(format("Git restore exception: %s", e.getMessage()));
        }
    }

    /**
     * Subscribes get the current workspace and branch.
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "subscribe", topicMatches = "git:state:/", permission="git_read")
    public void getGitState(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            String workspace = wsService.getWorkspace(session);
            String branch= gitService.getCurrentBranch(workspace);

            JsonObject result = Json.createObjectBuilder().add("content", Json.createObjectBuilder()
            .add("workspace", workspace)
            .add("branch", branch)).build();
            wsService.sendMessageToClient(session, "response", topic, result.toString());

        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "State Error", e.getMessage() );
            log.error("Git set workspace exception: ", e);
        }
    }

    /**
     * Subscribes to a list of commits for the current branch or file. The list is returned in a format
     * suitable for display by the DataTable component.
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "subscribe", topicMatches = "git:commits:/.*", permission="cms_user")
    public void getCommitsForCurrentBranch(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            String fileName = topic.substring("git:commits:/".length());
            String workspace = wsService.getWorkspace(session);
            String branch= gitService.getCurrentBranch(workspace);
            JsonObject commits = gitService.getCommitsForBranchOrFile(workspace, branch, fileName);
            wsService.sendMessageToClient(session, "response", topic, commits.toString());
            wsService.addSubscription(session, topic);
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "State Error", e.getMessage() );
            log.error("Git set workspace exception: ", e);
        }
    }

    /**
     * Finds commits that either need merging into develop or master, or that need a 'rebase' to bring them
     * into the current branch or that need a 'pull' to bring them into the current branch.
     * 
     * Examples:
     *      git:log:/
     *      git:log:/rebase
     *      git:log:/pull
     *      git:log:/merge
     *  
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "subscribe", topicMatches = "git:log:/.*", permission="cms_user")
    public void getDiff(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            boolean merge = topic.equals("git:log:/") || topic.equals("git:log:/merge");
            boolean rebase = topic.equals("git:log:/") || topic.equals("git:log:/rebase");
            boolean pull = topic.equals("git:log:/") || topic.equals("git:log:/pull");
            String workspace = wsService.getWorkspace(session);
            String branch= gitService.getCurrentBranch(workspace);

            JsonObject list = gitService.getLog(workspace, branch, merge, rebase, pull);

            wsService.sendMessageToClient(session, "response", topic, list.toString());
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Diff Error", e.getMessage() );
            log.error("Git diff exception: ", e);
        }
    }

    /**
     * Subscribes to get the current list of branches.
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "subscribe", topicMatches = "git:branches:/.*", permission="git_read")
    public void getBranchList(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            String workspace = wsService.getWorkspace(session);
            // gitService.getBranchList(workspace);
            Boolean excludeCurrentBranch = topic.endsWith("excludeCurrentBranch");

            List<String> branchList;
            if (topic.endsWith("all")) {
                branchList = gitService.getRepoBranchList();
            } else {
                branchList = gitService.getBranchList(workspace, excludeCurrentBranch);
            }
            

            JsonArrayBuilder builder = Json.createArrayBuilder();

            for (String branch : branchList) {
                JsonObject row = Json.createObjectBuilder().add("value", branch).add("label", branch).build();
                builder.add(row);
            }

            JsonArray result = builder.build();

            wsService.sendMessageToClient(session, "response", topic, result.toString());

        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Git get branches error:", e.getMessage() );
            log.error("Git get branches exception: ", e);
        }
    }

    /**
     * Subscribes to get the current branch for the workspace.
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "subscribe", topicMatches = "git:branch:/", permission="git_read")
    public void getCurrentBranch(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            String workspace = wsService.getWorkspace(session);
            String branch= gitService.getCurrentBranch(workspace);
            String result = "\"" + branch + "\"";
            wsService.sendMessageToClient(session, "response", topic, result);

        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Git get branch error:", e.getMessage() );
            log.error("Git get current branch exception: ", e);
        }
    }

    /**
     * Subscribes to "git status".
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "subscribe", topicMatches = "git:status:/", permission="git_read")
    public void gitSubscribeToStatus(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            JsonObject content = gitService.getStatus(wsService.getWorkspace(session));
            wsService.sendMessageToClient(session, "publish", topic, content.toString());
            wsService.addSubscription(session, topic);
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Git status error:", e.getMessage() );
            log.error("Git Status exception: ", e);
        }
    }

    /**
     * Performs a "git add" on a file to stage it.
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "publish", topicMatches = "git:add:/.*", permission="git_write")
    public void gitAddFile(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            String fileToAdd = topic.substring("git:add:/".length());
            gitService.addFile(wsService.getWorkspace(session), fileToAdd);
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Git add error:", e.getMessage() );
            log.error("Git Add exception: ", e);
        }
    }

    /**
     * Performs a "git rm" to stage a file for removal.
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "publish", topicMatches = "git:rm:/.*", permission="git_write")
    public void gitRemoveFile(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            String fileToRemove = topic.substring("git:rm:/".length());
            gitService.rmFile(wsService.getWorkspace(session), fileToRemove);
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Git rm error:", e.getMessage() );
            log.error("Git rm exception: ", e);
        }
    }

    /**
     * Performs a "git reset" on a file to unstage it.
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "publish", topicMatches = "git:unstage:/.*", permission="git_write")
    public void gitUnstageFile(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            String fileToUnstage = topic.substring("git:unstage:/".length());
            gitService.unstageFile(wsService.getWorkspace(session), fileToUnstage);
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Git add error:", e.getMessage() );
            log.error("Git Add exception: ", e);
        }
    }

    /**
     * Performs a "git commit" to commit changes. The staged chages are commited if there are any,
     * otherwise the Changed files are stagged and committed. The commit is pushed to the repo.
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "publish", topicMatches = "git:commit:/", permission="git_write")
    public void gitCommitStagedFiles(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            String commitMsg = JsonUtils.getString(message, "content");
            wsService.sendErrorToClient(session, topic, "Updating", "Please wait while the respository is updated...", INFO_SEVERITY);
            gitService.commitStagedFiles(wsService.getWorkspace(session), commitMsg, wsService.getName(session), wsService.getEmail(session));

            String commitBranch = gitService.getCurrentBranch(wsService.getWorkspace(session));

            // Publish a list of commits to any sessions that has subscribed on the same branch as the commit.
            List<Subscriber> subscribers = wsService.getSubscribers("git:commits:/");
            for (Subscriber subscriber : subscribers) {
                String workspace = wsService.getWorkspace(subscriber.getSession());
                String branch= gitService.getCurrentBranch(workspace);
                if (commitBranch.equals(branch)) {
                    JsonObject commits = gitService.getCommitsForBranchOrFile(workspace, branch, "");
                    wsService.sendMessageToClient(subscriber.getSession(), "response", "git:commits:/", commits.toString());
                }    
            }
            wsService.sendClearErrorToClient(session, topic);

        } catch (MissingValueException e) {
            wsService.sendErrorToClient(session, topic, "No Commit Message", e.getMessage());
            log.error(format("%s : %s", e.getMessage(), message.toString()));
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Git Commit Error:", e.getMessage() );
            log.error("Git commit exception: ", e);
        }
    }

    /**
     * Performs a "git stash" to stash changes
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "publish", topicMatches = "git:stash:/", permission="git_write")
    public void gitStash(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            String stashName = JsonUtils.getString(message, "content");
            gitService.stash(wsService.getWorkspace(session), stashName);
        }  catch (MissingValueException e) {
            wsService.sendErrorToClient(session, topic, "Missing Stash Name", e.getMessage());
            log.error(format("%s : %s", e.getMessage(), message.toString()));
        }
        catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Git stash error:", e.getMessage() );
            log.error("Git stash exception: ", e);
        }
    }

    /**
     * Performs a "git stash pop" to restore a stash.
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "publish", topicMatches = "git:stashpop:/.+", permission="git_write")
    public void gitStashPop(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            String stashRef = topic.substring("git:stashpop:/".length());
            gitService.stashPop(wsService.getWorkspace(session), stashRef);
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Git Stash Restore Error:", e.getMessage() );
            log.error("Git stash pop exception: ", e);
        }
    }

    /**
     * Performs a "git stash drop" to delete a stash.
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "publish", topicMatches = "git:stashdrop:/.+", permission="git_write")
    public void gitStashDrop(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            String stashRef = topic.substring("git:stashdrop:/".length());
            gitService.stashDrop(wsService.getWorkspace(session), stashRef);
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Git Stash Delete Error:", e.getMessage() );
            log.error("Git stash drop exception: ", e);
        }
    }

    /**
     * Performs a "git rebase" for the current branch. 
     * 
     * Example:
     *      git:rebase:/
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "request", topicMatches = "git:rebase:/", permission="git_write")
    public void gitRebase(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        String branch = "";
        try {
            topic = message.getString("topic");
            String workspace = wsService.getWorkspace(session);
            branch = gitService.getCurrentBranch(workspace);
            log.info(format("Performing a git pull from %s branch.", branch));  
            gitService.rebase(workspace, branch);
            wsService.sendMessageToClient(session, "response", topic, "{}");
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, format("Git rebase %s error:", branch), e.getMessage() );
            log.error(format("Git rebase %s exception: ", branch), e);
        }
    }

    /**
     * Performs a "git pull" for the current branch. 
     * 
     * Examples:
     *      git:pull:/
     *      git:pull:/master
     *      git:pull:/develop
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "request", topicMatches = "git:pull:/", permission="git_write")
    public void gitPull(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        String branch = "";
        try {
            topic = message.getString("topic");
            String workspace = wsService.getWorkspace(session);
            branch = gitService.getCurrentBranch(workspace);
            log.info(format("Performing a git pull from %s branch.", branch));  
            gitService.pull(workspace, branch);
            wsService.sendMessageToClient(session, "response", topic, "{}");
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, format("Git pull %s error:", branch), e.getMessage() );
            log.error(format("Git pull %s exception: ", branch), e);
        }
    }

    /**
     * DEPRECATED Performs a "git pull" from either the master or develop branch or the current branch. 
     * 
     * Examples:
     *      git:pull:/
     *      git:pull:/master
     *      git:pull:/develop
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "publish", topicMatches = "git:pull:/.*", permission="git_write")
    public void gitPullOld(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        String branch = "";
        try {
            topic = message.getString("topic");
            String workspace = wsService.getWorkspace(session);
            branch = topic.length() > 10 ? topic.substring(10) : gitService.getCurrentBranch(workspace);
            log.info(format("Performing a git pull from %s branch.", branch));  
            gitService.pull(workspace, branch);
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, format("Git pull %s error:", branch), e.getMessage() );
            log.error(format("Git pull %s exception: ", branch), e);
        }
    }

    /**
     * Gets the last git commited version of a file. Used for differences.
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "subscribe", topicMatches = "git:file:/.+", permission="git_read")
    public void gitFile(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        String filePath = "";
        try {
            topic = message.getString("topic");
            filePath = topic.substring("git:file:/".length());
            String fileContent = gitService.getLastCommitedFile(wsService.getWorkspace(session), filePath);
            wsService.sendMessageToClient(session, "publish", topic, fileContent, true);
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Issue with getting last committed version of " + filePath, e.getMessage() );
            log.error("Issue with getting last committed version of " + filePath, e);
        }
    }

   /**
     * Creates a new file, checking that the file doesn't already exist. Notifies any subscribers, 
     * including subscribers to git:status:/ and file:/. An alternative is just to piublish to
     * the topic file:/...
     *
     * Example
     * {"event": "publish", "topic": "git:new:/my_app/test.txt"}
     * 
     * @param session Web Socket session.
     * @param message JSON Object containing the event and topic.
     */
    @Event(value = "publish", topicMatches = "git:new:/.+\\..+$", permission="git_write") 
    public void newFile(@Session WebSocketSession session, @Message JsonObject message) {    
        String topic = "";
        try {
            topic = message.getString("topic");


            int index = topic.lastIndexOf('.');
            if (index == -1 || index == topic.length() - 1) {
                throw new Exception("The file name must end with a file extension.");
            }

            String fileTopic = topic.replace("git:new:", "file:");
            gitService.newFile(wsService.getWorkspace(session), fileTopic);

            // Publish to any sessions that have subscribed to the topic.
            List<Subscriber> subscribers = wsService.getSubscribers(fileTopic);
            for (Subscriber subscriber : subscribers) {
                wsService.sendMessageToClient(subscriber.getSession(), "publish", fileTopic, null);
            }

            // Publish to any sessions that have subscribed to the topic using "json:".
            if (fileTopic.endsWith(".json") || fileTopic.endsWith(".jsonc")) {      
                String jsonTopic = fileTopic.replace("file:", "json:");
                List<Subscriber> jsonSubscribers = wsService.getSubscribers(jsonTopic);
                for (Subscriber subscriber : jsonSubscribers) {
                    wsService.sendMessageToClient(subscriber.getSession(), "publish", jsonTopic, null);
                }
            }
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "New File Error", e.getMessage());
            log.error("New File exception: " + e.getMessage());
        }
    }

  /**
     * Creates a new folder, checking that the folder doesn't already exist. Notifies any subscribers, 
     * including subscribers to git:status:/ and file:/. Folders are distinguished from files by
     * whether or not there's a file extension.
     *
     * Example
     * {"event": "publish", "topic": "git:new:/my_app/new_folder"}
     * 
     * @param session Web Socket session.
     * @param message JSON Object containing the event and topic.
     */
    @Event(value = "publish", topicMatches = "git:new:/.+(?<!\\..+)$", permission="git_write") 
    public void newFolder(@Session WebSocketSession session, @Message JsonObject message) {    
        String topic = "";
        try {
            topic = message.getString("topic");

            String folderTopic = topic.replace("git:new:", "file:");
            gitService.newFolder(wsService.getWorkspace(session), folderTopic);

        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "New File Error", e.getMessage());
            log.error("New File exception: " + e.getMessage());
        }
    }

    /**
     * Deletes a file from the workspace. The subscribers notified by publishing
     * to the topic with a content value of null.
     * 
     * Example
     * {"event": "publish", "topic": "git:delete:/brill_cms/test.txt"}
     * 
     * @param session Web Socket session.
     * @param message JSON Object containing the event and topic.
     */
    @Event(value = "publish", topicMatches = "git:delete:/.+\\..+$", permission="git_write") 
    public void deleteFile(@Session WebSocketSession session, @Message JsonObject message) {    
        String topic = "";
        try {
            topic = message.getString("topic");
            
            String fileTopic = topic.replace("git:delete:", "file:");
            gitService.deleteFileOrDirectory(wsService.getWorkspace(session), fileTopic);

            // Publish to any sessions that have subscribed to the topic.
            List<Subscriber> subscribers = wsService.getSubscribers(fileTopic);
            for (Subscriber subscriber : subscribers) {
                wsService.sendMessageToClient(subscriber.getSession(), "publish", fileTopic, null);
            }

            // Publish to any sessions that have subscribed to the topic using "json:".
            if (fileTopic.endsWith(".json") || fileTopic.endsWith(".jsonc")) {      
                String jsonTopic = fileTopic.replace("file:", "json:");
                List<Subscriber> jsonSubscribers = wsService.getSubscribers(jsonTopic);
                for (Subscriber subscriber : jsonSubscribers) {
                    wsService.sendMessageToClient(subscriber.getSession(), "publish", jsonTopic, null);
                }
            }
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Server file deletion error.", e.getMessage());
            log.error("File delete exception.", e);
        }
    }

    /**
     * Deletes a folder from the workspace. Topics for directories and files are distinguished from
     * one another by whether there is a file extension.
     * 
     * Example
     * {"event": "publish", "topic": "git:delete:/my_app/my_foleder"}
     * 
     * @param session Web Socket session.
     * @param message JSON Object containing the event and topic.
     */
    @Event(value = "publish", topicMatches = "git:delete:/.+(?<!\\..+)$", permission="git_write") 
    public void deleteFolder(@Session WebSocketSession session, @Message JsonObject message) {    
        String topic = "";
        try {
            topic = message.getString("topic");
            
            String folderTopic = topic.replace("git:delete:", "file:");
            gitService.deleteFileOrDirectory(wsService.getWorkspace(session), folderTopic);

            // Publish to any sessions that have subscribed to the topic.
            List<Subscriber> subscribers = wsService.getSubscribers(folderTopic);
            for (Subscriber subscriber : subscribers) {
                wsService.sendMessageToClient(subscriber.getSession(), "publish", folderTopic, null);
            }
        } catch (GitServiceException e) {
            wsService.sendErrorToClient(session, topic, "Folder Deletion", e.getMessage());
            log.error("Folder delete:", e.getMessage());
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Folder Error", e.getMessage());
            log.error("Folder delete exception.", e);
        }
    }

    /**
     * Renames or moves a file. The content specifies the destination location.
     * 
     * Example
     * {"event": "publish", "topic": "git:mv:/brill_cms/test.txt", content: "brill_cms/test2.txt"}
     * 
     * @param session Web Socket session.
     * @param message JSON Object containing the event and topic.
     */
    @Event(value = "publish", topicMatches = "git:mv:/.*$", permission="git_write") 
    public void moveFile(@Session WebSocketSession session, @Message JsonObject message) {    
        String topic = "";
        String fromPath = "";
        String toPath = "";
        try {
            topic = message.getString("topic");
            int index = topic.lastIndexOf('.');
            if (index == -1 || index == topic.length() - 1) {
                throw new Exception("The topic must end with a file extension.");
            }

            fromPath = topic.replace("git:mv:", ""); 
            toPath = "/" + message.getString("content");

            if (fromPath.contains("..") || toPath.contains("..")) {
                wsService.sendErrorToClient(session, topic, "Security Violation.", "Illegal rename/move attempted.");
                log.error(format("Move path contains '..' fromPath =  %s toPath = %s", fromPath, toPath));
                return;
            }

            gitService.moveFile(wsService.getWorkspace(session), fromPath, toPath);

        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Rename/Move failed.", 
                format("Unable to move %s to %s", fromPath, toPath));
            log.error(format("Unable to move %s to %s", fromPath, toPath));
        }
    }

    /**
     * Duplicates a file in the workspace. 
     * 
     * Example
     * {"event": "publish", "topic": "git:duplicate:/brill_cms/test.txt"}
     * 
     * @param session Web Socket session.
     * @param message JSON Object containing the event and topic.
     */
    @Event(value = "publish", topicMatches = "git:duplicate:/.+\\..+$", permission="git_write") 
    public void duplicateFile(@Session WebSocketSession session, @Message JsonObject message) {    
        String topic = "";
        try {
            topic = message.getString("topic");
            
            String fileTopic = topic.replace("git:duplicate:", "file:");
            gitService.duplicateFile(wsService.getWorkspace(session), fileTopic);

        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "File Duplication Error.", e.getMessage());
            log.error("File duplication exception.", e);
        }
    }

    /**
     * Duplicates a folder in the workspace. 
     * 
     * Example
     * {"event": "publish", "topic": "git:duplicate:/brill_cms/Resouces"}
     * 
     * @param session Web Socket session.
     * @param message JSON Object containing the event and topic.
     */
    @Event(value = "publish", topicMatches = "git:duplicate:/.+(?<!\\..+)$", permission="git_write") 
    public void duplicateFolder(@Session WebSocketSession session, @Message JsonObject message) {    
        String topic = "";
        try {
            topic = message.getString("topic");
            
            String fileTopic = topic.replace("git:duplicate:", "file:");
            gitService.duplicateFolder(wsService.getWorkspace(session), fileTopic);

        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Folder Duplication Error.", e.getMessage());
            log.error("File duplication exception.", e);
        }
    }


    /**
     * Refresh the Pending Changes and Files tree.
     * 
     * The user only needs to refresh the changes and file tree when git commands have been issued 
     * directly from the command line or using some other application.
     * 
     * Example
     * {"event": "publish", "topic": "git:refresh:/"}
     * 
     * @param session Web Socket session.
     * @param message JSON Object containing the event and topic.
     */
    @Event(value = "publish", topicMatches = "git:refresh:/", permission="git_read") 
    public void refresh(@Session WebSocketSession session, @Message JsonObject message) {    
        String topic = "";
        try {
            gitService.refresh(wsService.getWorkspace(session));

        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Refresh Error.", e.getMessage());
            log.error("Refresh Exception.", e);
        }
    }
}