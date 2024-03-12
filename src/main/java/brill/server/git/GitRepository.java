// © 2021 Brill Software Limited - Git Package, distributed under the MIT License.
package brill.server.git;

import brill.server.exception.GitServiceException;
import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.RebaseCommand.Operation;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.BranchConfig;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import java.util.Set;
import static java.lang.String.format;

/**
 * Git Repository
 * 
 * Supports git repository access. The git repo holds the config details of each
 * brill app. Authentication is using a public and private key that must be
 * setup in ~/.ssh/id_rsa. The private key must be in RSA format with no
 * passphrase. OPENSSH is not supported. An OPENSSH format file can be converted
 * to RSA format. See the README.
 */
public class GitRepository {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GitRepository.class);

    public static String MASTER = "master";
    public static String DEVELOP = "develop";

    public static String PRODUCTION_WORKSPACE = "production";
    public static String DEVELOPMENT_WORKSPACE = "development";

    public static String BRANCH = "branch";
    public static String LEAF = "leaf";

    public static int MAX_ROWS_TO_RETURN = 1000;

    private String remoteRepositoryUrl; // Default repo URL.
    private String localRepoDir; // Directory under which the workspaces are held.

    public GitRepository() {
        remoteRepositoryUrl = "";
        localRepoDir = "";
    }

    public GitRepository(String remoteRepositoryUrl, String localRepoDir) {
        this.remoteRepositoryUrl = remoteRepositoryUrl;
        this.localRepoDir = localRepoDir;
    }

    /**
     * Clones the remote repository to a local directory.
     *
     * @throws GitServiceException
     */
    public void cloneRemoteRepository(String repository, String workspace, String branch) throws GitServiceException {
        Git git = null;
        if (repository == null || repository.length() == 0) {
            repository = this.remoteRepositoryUrl; // Use default repository
        }
        try {
            File localPath = new File(format("%s/%s", localRepoDir, workspace));

            log.info("Cloning from " + repository + " to " + localPath);
    
            SshdSessionFactory sshdSessionFactory = new SshdSessionFactory(); // Apache SSH Driver
            git = Git.cloneRepository().setURI(repository)
                    .setTransportConfigCallback(new TransportConfigCallback() {
                        @Override
                         public void configure(Transport transport) {
                             SshTransport sshTransport = (SshTransport) transport;
                             sshTransport.setSshSessionFactory(sshdSessionFactory);
                         }
    
                 }).setDirectory(localPath).setBranch(branch).call();
            log.info("Completed downloading repository to " + git.getRepository().getDirectory());
        } catch (InvalidRemoteException ire) {
            log.error(format("Remote git respository %s not found.", repository));
            throw new GitServiceException(format("Remote git respository %s not found.", repository), ire);
        } catch (TransportException te) {
            if (te.getMessage().contains("invalid privatekey")) {
                log.error(
                        "Unable to read private key from ~/.ssh/id_key. The file needs to be converted from OPENSSH to RSA format.");
                throw new GitServiceException(
                        "Unable to read private key from ~/.ssh/id_key possible because the format is openssh", te);
            }
            throw new GitServiceException(
                    "Transport exception while accessing remote git respository " + repository, te);
        } catch (GitAPIException gae) {
            throw new GitServiceException(
                    "Git API exception while accessing remote git respository " + repository, gae);
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

    /**
     * Clones a repository using the default repository.
     * 
     * @param workspace
     * @param branch
     * @throws GitServiceException
     */
    public void cloneRemoteRepository(String workspace, String branch) throws GitServiceException {
        cloneRemoteRepository(remoteRepositoryUrl, workspace, branch);
    }

    /**
     * Performs a git pull on a branch.
     * 
     * @param branch The branch to pull (either master or develop).
     * @return List of files pulled.
     * @throws GitServiceException
     */
    public ArrayList<String> pull(String workspace, String branch) throws GitServiceException {
        Git git = null;
        PullResult pullResult = null;
        try {
            log.info(format("Pull for branch %s", branch));
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, workspace));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);
            PullCommand pull = git.pull();
            try {
                // TO DO pullResult = pull.setRemote("origin").setRemoteBranchName(branch).call();
                // pullResult = pull.setRemote("origin").setRemoteBranchName(branch).setRebase(true).call();
                pullResult = pull.setRebase(false).call();
            } catch (GitAPIException e) {
                if (e.getMessage().contains("Cannot check out from unborn branch")) {
                    git.rebase().setOperation(Operation.SKIP).call();
                    pullResult = pull.call();
                } else {
                    throw new GitServiceException(format("Unable to perform Pull: %s", e.getMessage()));
                }
            }
            
            return getPulledFileList(repo, branch, pullResult);

        } catch (IOException ioe) {
            throw new GitServiceException(format("IOException when attempting a git pull from %s", localRepoDir), ioe);
        } catch (GitAPIException gae) {
            log.error(format("GitAPI Exception when attempting a git pull from %s", localRepoDir), gae);    
            log.warn("The network connection to the git respository might be down. Continuing with a respository that could be out of date.");
            throw new GitServiceException(format("Exception when attempting a git pull from %s", localRepoDir), gae);
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

   /**
     * Performs a git rebase on a branch. Assumes the rebase is from remotes/<remote>/develop.
     * An enhancemnt would be to find the branches start point and rebase using that.
     * 
     * @param branch The branch to pull (either master or develop).
     * @return List of files pulled.
     * @throws GitServiceException
     */
    public ArrayList<String> rebase(String workspace, String branch) throws GitServiceException {
        Git git = null;
        RebaseResult rebaseResult = null;
        try {
            log.info(format("Rebase for branch %s", branch));
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, workspace));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);
            String remoteBranch = this.getTrackingBranch(workspace);
            int lastSlash = remoteBranch.lastIndexOf("/");
            String remoteRebaseBranch = remoteBranch.substring(0, lastSlash) + "/develop";
            RebaseCommand rebase = git.rebase().setUpstream(repo.resolve(remoteRebaseBranch));         
            rebaseResult = rebase.call();
            return getRebaseFileList(repo, branch, rebaseResult);

        } catch (IOException ioe) {
            throw new GitServiceException(format("IOException when attempting a git pull from %s", localRepoDir), ioe);
        } catch (GitAPIException gae) {
            log.error(format("GitAPI Exception when attempting a git rebease from %s", localRepoDir), gae);    
            log.warn("The network connection to the git respository might be down. Continuing with a respository that could be out of date.");
            throw new GitServiceException(format("Exception when attempting a git pull from %s<br/>%s", localRepoDir, gae.getMessage()), gae);
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

    /**
     * Finds all the files that were rebased.
     * 
     * @param repo
     * @param branch
     * @param pullResult List of file paths for the files pulled.
     * @return
     */
    private ArrayList<String> getRebaseFileList(Repository repo, String branch, RebaseResult rebaseResult) {
        ArrayList<String> fileList = new ArrayList<String>();
        // Find rebased files.
        return fileList;
    }

    /**
     * Finds all the files that were pulled.
     * 
     * @param repo
     * @param branch
     * @param pullResult List of file paths for the files pulled.
     * @return
     */
    private ArrayList<String> getPulledFileList(Repository repo, String branch, PullResult pullResult) {
        ArrayList<String> fileList = new ArrayList<String>();
        try {
            FetchResult fetchResult = pullResult.getFetchResult();
            if (fetchResult == null) {
                return fileList;
            }
            Collection<TrackingRefUpdate> updates = fetchResult.getTrackingRefUpdates();
            for (TrackingRefUpdate update : updates) {
                if (!update.getLocalName().endsWith(branch)) {
                    continue;
                }
                RevWalk walk = new RevWalk(repo);
                RevCommit newObjTree = walk.parseCommit(update.getNewObjectId());
                RevCommit oldObjTree = walk.parseCommit(update.getOldObjectId());
                ObjectId newTree = newObjTree.getTree();
                ObjectId oldTree = oldObjTree.getTree();
                TreeWalk tw = new TreeWalk(repo);
                tw.setRecursive(true);
                tw.addTree(oldTree);
                tw.addTree(newTree);
                List<DiffEntry> diffs = DiffEntry.scan(tw);
                DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
                diffFormatter.setRepository(repo);
                diffFormatter.setContext(0);
                for (DiffEntry entry : diffs) {
                    FileHeader header = diffFormatter.toFileHeader(entry);
                    fileList.add(header.getNewPath());
                }
                diffFormatter.close();
                walk.close();
            } 
        } catch (IOException e) {
                log.error("IOExpetion when performing git pull",e);
        }
        return fileList;
    }


    public void cloneOrPull(String workspace, String branch) throws GitServiceException {
        if (localRepoExits(workspace)) {
            pull(workspace, branch);
        } else {
            cloneRemoteRepository(workspace, branch);
        }
    }

    /**
     * Returns true if the local repository already exists and has the remote repository URL.
     * 
     * @return true if local repo exists.
     */
    public boolean localRepoExits(String workspace) {
        File localPath = new File(format("%s/%s", localRepoDir, workspace));
        if (!localPath.exists() || !localPath.isDirectory()) {
            return false;
        }
        File gitDir = new File(format("%s/%s/.git", localRepoDir, workspace));
        if (!gitDir.exists() || !gitDir.isDirectory()) {
            return false;
        }
        File gitConfig = new File(format("%s/%s/.git/config", localRepoDir, workspace));
        if (!gitConfig.exists() || !gitConfig.isFile()) {
            return false;
        }

        try {
            String content = new String ( Files.readAllBytes( Paths.get(format("%s/%s/.git/config", localRepoDir, workspace))));
            if (content.contains("url = " + this.remoteRepositoryUrl)) {
                return true;
            }
        } catch (IOException ex) {
        }

        return false;
    }

    public boolean doesWorkspaceAlreadyExist(String workspace) {
        File localPath = new File(format("%s/%s", localRepoDir, workspace));
        if (!localPath.exists() || !localPath.isDirectory()) {
            return false;
        }
        File gitDir = new File(format("%s/%s/.git", localRepoDir, workspace));
        if (!gitDir.exists() || !gitDir.isDirectory()) {
            return false;
        }
        File gitConfig = new File(format("%s/%s/.git/config", localRepoDir, workspace));
        if (!gitConfig.exists() || !gitConfig.isFile()) {
            return false;
        }
        return true;
    }

    /**
     * Deletes the local repository. Only used by tests.
     * 
     * @throws GitServiceException
     */
    public void deleteLocalRepo(String workspace) throws GitServiceException {
        try {
                File directory = new File(format("%s/%s", localRepoDir, workspace));
                if (directory.exists() && directory.isDirectory()) {
                    FileUtils.deleteDirectory(directory);
                }
        } catch (IOException ioe) {
            log.error(format("Failed to delete local git repository %s", localRepoDir));
            throw new GitServiceException(format("Failed to delete local git repository %s", localRepoDir), ioe);
        }
    }

    /**
     * Gets the file tree.
     * 
     * @param appName
     * @return File tree
     * @throws GitServiceException
     */
    public JsonObject getFileTree(String branch, String appName, String topic, boolean includeFileContent) throws GitServiceException {
        String fullPath = "";
        try {
            if (topic.equals("/")) {
                topic = "";
            }
            return processTreeNode(format("%s/%s/%s", localRepoDir, branch, appName), topic, includeFileContent, null);
        } catch (Exception ioe) {
            throw new GitServiceException(format("IOException when reading from %s", fullPath), ioe);
        }
    }

    public JsonObject getFileTree(String branch, String path, boolean includeFileContent, List<String> hiddenApps) throws GitServiceException {
        String fullPath = "";
        try {
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return processTreeNode(format("%s/%s", localRepoDir, branch), path, includeFileContent, hiddenApps);
        } catch (Exception ioe) {
            throw new GitServiceException(format("IOException when reading from %s", fullPath), ioe);
        }
    }

    private JsonObject processTreeNode(String root, String path, boolean includeFileContent, List<String> hiddenApps) throws IOException {
        JsonObjectBuilder objBuilder = Json.createObjectBuilder();
        File file = new File(root + path);
        if (file.isFile()) {
            objBuilder.add("id", "file:" + path);
            objBuilder.add("name", path.substring(path.lastIndexOf('/') + 1));
            objBuilder.add("tooltip", "");
            objBuilder.add("type", LEAF);
            if (includeFileContent) {
                byte[] fileContent = FileUtils.readFileToByteArray(file);
                String encodedString = Base64.getEncoder().encodeToString(fileContent);
                objBuilder.add("base64",encodedString);
            }
            return objBuilder.build();
        }
        
        // It's a directory
        if (path.length() == 0) {
            objBuilder.add("id", "file:/");
            objBuilder.add("name", "/");
            objBuilder.add("tooltip", "");
            objBuilder.add("type", BRANCH);
        } else {
            objBuilder.add("id", "file:" + path);
            objBuilder.add("name", path.substring(path.lastIndexOf('/') + 1));
            objBuilder.add("tooltip", "");
            objBuilder.add("type", BRANCH);
        }
       
        String[] contents = file.list();
        if (contents == null) {
            return objBuilder.build();
        }

        Arrays.sort(contents, new Sort());
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        for (String child : contents) {
            if (!child.equals(".DS_Store") && !child.equals(".git") && 
                (hiddenApps == null || !hiddenApps.contains(child))) {
                arrayBuilder.add(processTreeNode(root, path + "/" + child, includeFileContent, hiddenApps));
            }   
        }
        objBuilder.add("children", arrayBuilder.build());
        return objBuilder.build();
    }

    /**
     * Saves a file tree. Used by the CMS Copy and Paste for copying directories.
     * 
     * @param appName
     * @return File tree
     * @throws GitServiceException
     */
    public void saveFileTree(String workspace, String path, JsonObject content) throws GitServiceException {
        String fullPath = "";
        try {
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String rootId = content.getString("id");
            String idBase = rootId.substring(0, rootId.lastIndexOf("/"));
            saveTreeNode(workspace, format("%s/%s", localRepoDir, workspace), path, content, idBase);
        } catch (Exception ioe) {
            throw new GitServiceException(format("IOException when reading from %s", fullPath), ioe);
        }
    }

    private void saveTreeNode(String workspace, String root, String path, JsonObject node, String idBase) throws GitServiceException {
        if (node.getString("type").equals("branch")) {
            String newDir =  path + node.getString("id").substring(idBase.length());
            newFolder(workspace, newDir);
            JsonArray children = node.getJsonArray("children");
            for (int i = 0; i < children.size(); i++) {
                saveTreeNode(workspace, root, path,children.getJsonObject(i), idBase);
            }

        } else {
            String newFile =  path + node.getString("id").substring(idBase.length());
            byte[] fileContent = Base64.getDecoder().decode(node.getString("base64"));
            createOrUpdateFile(workspace, newFile, fileContent, false);
        }
    }
    
    public String getFile(String branch, String path) throws GitServiceException {
        String fullPath = "";
        try {
            fullPath = format("%s/%s%s", localRepoDir, branch, path);
            File file = new File(fullPath);
            if (file.exists() && file.isFile()) {
                return new String(Files.readAllBytes(file.toPath()));
            }
        } catch (IOException ioe) {
            throw new GitServiceException(format("IOException when reading from %s", fullPath), ioe);
        }

        throw new GitServiceException(format("Failed to find file %s", fullPath));
    }

    public String getFileBase64Encoded(String branch, String path) throws GitServiceException {
        String fullPath = "";
        try {
            fullPath = format("%s/%s%s", localRepoDir, branch, path);
            File file = new File(fullPath);
            if (file.exists() && file.isFile()) {
                byte[] fileContent = FileUtils.readFileToByteArray(file);
                String encodedString = Base64.getEncoder().encodeToString(fileContent);
                return encodedString;
            }
        } catch (IOException ioe) {
            throw new GitServiceException(format("IOException when reading from %s", fullPath), ioe);
        }

        throw new GitServiceException(format("Failed to find file %s", fullPath));
    }

    public String getLastCommitedFile(String branch, String path) throws GitServiceException {
        ObjectReader reader = null;
        RevWalk walk = null;
        TreeWalk treewalk = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, branch));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            reader = repo.newObjectReader();

            ObjectId lastCommitId = repo.resolve(Constants.HEAD);
            
            // Get the commit object for that revision
            walk = new RevWalk(reader);
            RevCommit commit = walk.parseCommit(lastCommitId);

            // Get the revision's file tree
            RevTree tree = commit.getTree();
            // .. and narrow it down to the single file's path
            treewalk = TreeWalk.forPath(reader, path, tree);

            if (treewalk != null) {
                // use the blob id to read the file's data
                byte[] data = reader.open(treewalk.getObjectId(0)).getBytes();
                return new String(data, "utf-8");
            } else {
                return "";
            }

        } catch (IOException e) {
            throw new GitServiceException(format("Unable to get last commited file %s on branch %s", path, branch), e);
        } finally {
            if (reader != null) {
                reader.close();
            }
            if (walk != null) {
                walk.close();
            }
            if (treewalk != null) {
                treewalk.close();
            }
        }
    }

    /**
     * Creates or updates a file. 
     * 
     * @param branch
     * @param path
     * @param newContent Array of bytes.
     * @param noOverwrite
     * @throws GitServiceException
     */
    public void createOrUpdateFile(String branch, String path, byte[] newContent, boolean noOverwrite) throws GitServiceException {
        String fullPath = format("%s/%s%s", localRepoDir, branch, path);
        try {
            File file = new File(fullPath);
            int copyNumber = 0;
            while (file.exists() && noOverwrite && copyNumber < 100) {
                if (copyNumber++ > 100) {
                    throw new GitServiceException(format("Unable to create unique file name. Path = %s", path));
                }
                int lastDotPos = path.lastIndexOf(".");
                if (lastDotPos == -1) {
                    throw new GitServiceException(format("File Topic requires a file name that has an extension. Path = %s", path));
                }
                String newPath = path.substring(0, lastDotPos) + "(" + copyNumber + ")" + path.substring(lastDotPos);
                String newFullPath = format("%s/%s%s", localRepoDir, branch, newPath);
                file = new File(newFullPath);
            }
            Files.write(file.toPath(), newContent);
        } catch (AccessDeniedException e) {
            throw new GitServiceException(format("File is write protected.<br />Sorry but you you are not allowed to modify %s", fullPath)); 
        }
        catch (IOException e) {
            throw new GitServiceException(format("Unable to create or update %s", fullPath), e); 
        }
    }

    public void createOrUpdateFile(String branch, String path, byte[] newContent) throws GitServiceException {
        createOrUpdateFile(branch, path, newContent, false);
    }

    public void newFile(String branch, String path) throws GitServiceException {
        String fullPath = format("%s/%s%s", localRepoDir, branch, path);
        try {
            File file = new File(fullPath);
            if (file.exists()) {
                throw new GitServiceException(format("Unable to create %s as it already exists.", path));
            }
            boolean success = file.createNewFile();
            if (!success) {
                throw new GitServiceException(format("Unable to create %s", path));
            }
        } catch (IOException e) {
            throw new GitServiceException(format("Unable to create %s", fullPath), e);
        }
    }

    public void newFolder(String branch, String path) throws GitServiceException {
        String fullPath = format("%s/%s%s", localRepoDir, branch, path);
        try {
            Files.createDirectories(Paths.get(fullPath));
        } catch (IOException e) {
            throw new GitServiceException(format("Unable to create folder %s", fullPath), e);
        }
    }

    public void deleteFileOrDirectory(String branch, String path) throws GitServiceException {
        String fullPath = format("%s/%s%s", localRepoDir, branch, path);
        try {
            File file = new File(fullPath);
            if (file.isDirectory() && file.listFiles().length > 0) {
                FileUtils.deleteDirectory(file); // Deletes subdirectories and files as well.
            } else {
                boolean success = file.delete();
                if (!success) {
                    throw new GitServiceException(format("Unable to delete %s", fullPath));
                }
            }         
        } catch (SecurityException e) {
            throw new GitServiceException(format("Delete failed for %s", fullPath), e);
        } catch (IOException e) {
            throw new GitServiceException(format("Unable to delete directory %s", fullPath), e);
        }
    }

    /**
     * Renames or moves a file. Alothough there's a "git mv" command, this is not supported
     * by JGit, so we just move the file within the file system using the Java Files.move() method. 
     * 
     * @param branch
     * @param path
     * @param newPath
     * @throws GitServiceException
     */
    public void moveFile(String workspace, String path, String newPath) throws GitServiceException {
        String source = format("%s/%s%s", localRepoDir, workspace, path);
        String target = format("%s/%s%s", localRepoDir, workspace, newPath);
        try {
            File sourceFile = new File(source);
            File targetFile = new File(target);
            Files.move(sourceFile.toPath(), targetFile.toPath());
        } catch (Exception e) {
            throw new GitServiceException(format("Unable to move %s to destination", source, target), e);
        }
    }

    public void duplicateFile(String branch, String path) throws GitServiceException {
        String fullPath = format("%s/%s%s", localRepoDir, branch, path);
        try {

            int dotPos = fullPath.lastIndexOf(".");
            if (dotPos == -1) {
                throw new GitServiceException(format("Unable to find last dot in %s", path));
            }
            String newPath = fullPath.substring(0, dotPos) + "_copy" + fullPath.substring(dotPos);
            
            File sourceFile = new File(fullPath);
            File newFile = new File(newPath);

            Files.copy(sourceFile.toPath(), newFile.toPath());

        } catch (IOException e) {
            throw new GitServiceException(format("Unable to duplicate file %s", path), e);
        }
    }

    public void duplicateFolder(String branch, String path) throws GitServiceException {
        String fullPath = format("%s/%s%s", localRepoDir, branch, path);
        try {
            String newPath = fullPath + "_copy";
            File sourceFolder = new File(fullPath);
            File newFolder = new File(newPath);
            FileUtils.copyDirectory(sourceFolder, newFolder);
    
        } catch (IOException e) {
            throw new GitServiceException(format("Unable to duplicate folder %s", path), e);
        }
    }

   /**
     * Performs a "git status"
     * 
     * @param branch
     * @throws GitServiceException
     */
    public JsonObject status(String branch) throws GitServiceException {
        Git git = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, branch));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo); 
            Status status = git.status().call();

            StashListCommand stashList = git.stashList();
            Collection<RevCommit> stashedRefs = stashList.call();
            return convertStatusToJson(status, stashedRefs);

        } catch (IOException | GitAPIException e) {
            throw new GitServiceException(format("Unable to get status for branch %s", branch), e);
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

   /**
     * Gets a list of all the branches on the repository.
     * 
     * @param branch
     * @throws GitServiceException
     */
    public List<String> getRepoBranchList(String workspace) throws GitServiceException {
        Git git = null;
        try {
            List<String> branchList = fetchGitBranches(getRemoteRepo(workspace));

            return branchList;
        } 
        finally {
            if (git != null) {
                git.close();
            }
        }
    }

    /**
     * Performs a "git branch" to return all the branches in workspace. Doesn't include remote branches in repository.
     * 
     * @param branch
     * @throws GitServiceException
     */
    public List<String> getBranchList(String workspace, boolean excludeCurrentBranch) throws GitServiceException {
        Git git = null;
        try {

            Path repoPath = Paths.get(format("%s/%s", localRepoDir, workspace));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);

            String exclude = "";
            if (excludeCurrentBranch) {
                String fullBranch = repo.getFullBranch();
                exclude = fullBranch.substring(fullBranch.lastIndexOf("/") + 1);
            }

            ListBranchCommand branchListCmd = git.branchList();
            List<Ref> branchList = branchListCmd.call();

            List<String> result = new ArrayList<String>();
            for (Ref ref : branchList) {
                String branch = ref.getName().substring(ref.getName().lastIndexOf("/")+1, ref.getName().length());
                if (!excludeCurrentBranch || !branch.equals(exclude)) {
                    result.add(branch);
                }
            }
            return result;

        } catch (IOException | GitAPIException e) {
            throw new GitServiceException("Unable to get a list of the workspace branches.", e);
        }
        finally {
            if (git != null) {
                git.close();
            }
        }
    }

    public String getCurrentBranch(String workspace) throws GitServiceException {
        //Git git = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, workspace));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            // git = new Git(repo);
        
            String fullBranch = repo.getFullBranch();
           
            // String fullBranch = branchList.get(0).getName();
            String branch = fullBranch.substring(fullBranch.lastIndexOf("/") + 1);
            return branch;

        } catch (IOException e) {
            throw new GitServiceException("Unable to get current brnach.", e);
        }
        // finally {
        //     if (git != null) {
        //         git.close();
        //     }
        // }
    }


    public List<String> fetchGitBranches(String gitUrl) throws GitServiceException {
        Collection<Ref> refs;
        List<String> branches = new ArrayList<String>();
        try {
            refs = Git.lsRemoteRepository()
                    .setHeads(true)
                    .setRemote(gitUrl)
                    .call();
            for (Ref ref : refs) {
                branches.add(ref.getName().substring(ref.getName().lastIndexOf("/")+1, ref.getName().length()));
            }
            Collections.sort(branches);
        } catch (InvalidRemoteException e) {
            throw new GitServiceException(" InvalidRemoteException occured in fetchGitBranches",e);
        } catch (TransportException e) {
            throw new GitServiceException(" TransportException occurred in fetchGitBranches",e);
        } catch (GitAPIException e) {
            throw new GitServiceException(" GitAPIException occurred in fetchGitBranches",e);
        }
        return branches;
    }

    public String createNewBranch(String workspace, String existingBranch, String newBranch) throws GitServiceException {
        Git git = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, workspace));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);

            git.branchCreate().setUpstreamMode(SetupUpstreamMode.TRACK).setName(newBranch).setStartPoint("origin/" + existingBranch).call();

            git.push().setRemote("origin").setRefSpecs(new RefSpec(newBranch + ":" + newBranch)).call();

            // We've created the branch. Now need to checkout the branch.

            git.checkout().setName(newBranch).call();

            return "success";

        } catch (IOException | GitAPIException e) {
            throw new GitServiceException(" Unable to create new branch: " + e.getMessage());
        }
        finally {
            if (git != null) {
                git.close();
            }
        }
    }

    public void switchBranch(String workspace, String newBranch) throws GitServiceException {
        Git git = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, workspace));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);

            git.checkout().setName(newBranch).call();

        } catch (IOException | GitAPIException e) {
            throw new GitServiceException(" Unable to switch branch: " + e.getMessage());
        }
        finally {
            if (git != null) {
                git.close();
            }
        }
    }

    public String mergeBranch(String workspace, String mergeBranch) throws GitServiceException {
        Git git = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, workspace));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);

            ListBranchCommand branchListCmd = git.branchList();
            List<Ref> branchList = branchListCmd.call();

            Ref branchRef = null;

            for (Ref ref : branchList) {
                String currentName = ref.getName().substring(ref.getName().lastIndexOf("/")+1, ref.getName().length());
                if (currentName.equals(mergeBranch)) {
                    branchRef = ref;
                    break;
                }
            }
            if (branchRef == null) {
                throw new GitServiceException(format("Unable to find ref for branch %s", mergeBranch));
            }

            MergeCommand mergeCmd = git.merge();
            MergeResult mergeResult = mergeCmd.include(branchRef).call();
            git.push().call();

            return mergeResult.getMergeStatus().toString();

        } catch (IOException | GitAPIException e) {
            throw new GitServiceException(" Unable to create new branch: " + e.getMessage());
        }
        finally {
            if (git != null) {
                git.close();
            }
        }
    }

    public void deleteBranch(String workspace, String branch, boolean deleteFromRepo, boolean forceDelete) throws GitServiceException {
        Git git = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, workspace));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);

            // Check that we're not attempting to delete any protected branches.
            if ((workspace.equals(PRODUCTION_WORKSPACE) && branch.equals(MASTER)) || 
                (workspace.equals(DEVELOPMENT_WORKSPACE) && branch.equals(DEVELOP)) ) {
                throw new GitServiceException(format("You are not allowed to delete the <b>%s</b> branch from the <b>%s</b> workspace.", branch, workspace));
            }
            if (deleteFromRepo && (branch.equals(DEVELOP) || branch.equals(MASTER))) {
                throw new GitServiceException(format("You are not allowed to delete the <b>%s</b> branch from the repository.", branch));
            }

            // Check if the current branch is the branch to be deleted. If so, switch branch to develop or master.
            String currentFullBranch = repo.getFullBranch();
            String currentBranch = currentFullBranch.substring(currentFullBranch.lastIndexOf("/") + 1);
            if (currentBranch.equals(branch)) {
                try {
                    if (branch.equals(DEVELOP)) {
                        git.checkout().setName(MASTER).call();
                    } else {
                        if (branch.equals(MASTER)) {
                            git.checkout().setName(DEVELOP).call();
                        } else {
                            try {
                                git.checkout().setName(DEVELOP).call();
                            } catch (Exception e) {
                                git.checkout().setName(MASTER).call();
                            }
                        }
                    }
                } catch (Exception e) {
                    throw new GitServiceException("You are trying to delete the current branch. Please switch to another branch first.");
                }
            }

            DeleteBranchCommand deleteCmd = git.branchDelete();
            List<String> branchList = deleteCmd.setForce(forceDelete).setBranchNames(branch).call();
            log.info(branchList.toString());

            // Delete from Repo
            if (deleteFromRepo) {
                RefSpec refSpec = new RefSpec(":refs/heads/" + branch);
                git.push().setRefSpecs(refSpec).setRemote("origin").call();
            }

        } catch (NotMergedException e) {
            throw new GitServiceException("Branch was not deleted as it has not been merged yet. Check the Force Delete checkbox to delete anyway.");
        } catch (IOException | GitAPIException e) {
            throw new GitServiceException(" Unable to delete branch: " + e.getMessage());
        }
        finally {
            if (git != null) {
                git.close();
            }
        }
    }

    public void checkoutBranch(String workspace, String branch) throws GitServiceException {
        Git git = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, workspace));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);  
            git.branchCreate().setForce(true).setName(branch).setStartPoint("origin/" + branch).call();
            git.checkout().setUpstreamMode(SetupUpstreamMode.TRACK).setName(branch).call();
            
        } catch (IOException | GitAPIException e) {
            throw new GitServiceException(" Unable to create new branch: " + e.getMessage());
        }
        finally {
            if (git != null) {
                git.close();
            }
        }
    }

    /**
     * Returns a table with all the commits for a branch or file.
     * 
     * 
     * @param workspace
     * @param branch
     * @param fileName
     * @return
     * @throws GitServiceException
     */
    public JsonObject getCommitsForBranchOrFile(String workspace, String branch, String fileName) throws GitServiceException {
        Git git = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, workspace));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);  
            
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd kk:mm");

            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            int rowCount = 0;

            LogCommand logCmd = git.log();
            if (fileName.length() > 0) {
                logCmd.addPath(fileName);
            } else {
                logCmd.add(repo.resolve(branch));
            }
            Iterable<RevCommit> gitLog = logCmd.call(); //git.log().add(repo.resolve(branch)).call();

            for (RevCommit commit : gitLog) {
                JsonObjectBuilder objBuilder = Json.createObjectBuilder();
                objBuilder.add("commit", commit.getName().substring(0,7));
                objBuilder.add("message", commit.getFullMessage());
                objBuilder.add("author", commit.getAuthorIdent().getName());
                objBuilder.add("date", dateFormat.format(commit.getAuthorIdent().getWhen()));
                arrayBuilder.add(objBuilder.build());
                if (++rowCount == MAX_ROWS_TO_RETURN) {
                    break;
                }
            }         
            JsonObject result = Json.createObjectBuilder().add("data", arrayBuilder.build())
                                                            .add("offset", 0)
                                                            .add("row_count", rowCount)
                                                            .add("title", fileName.length() > 0 ? fileName : branch).build();
            return result;
        } catch (IOException | GitAPIException e) {
            throw new GitServiceException("Unable to get commits for branch: " + e.getMessage());
        }
        finally {
            if (git != null) {
                git.close();
            }
        }      
    }

    public JsonObject getLog(String workspace, String branch, boolean merge, boolean rebase, boolean pull) throws GitServiceException {
        Git git = null;
        try {
            String compareBranch = branch.equals("develop") ? "master" : "develop";
 
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, workspace));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);

            git.fetch().call();

            String remoteTrackingBranch = new BranchConfig(repo.getConfig(), repo.getBranch()).getTrackingBranch();
            // String remote = remoteTrackingBranch.substring(5, remoteTrackingBranch.lastIndexOf("/") + 1); // e.g. remotes/origin/
            String remote = getRemote(remoteTrackingBranch);

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd kk:mm");
            
            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            int rowCount = 0;
            
            if (merge) {
                Iterable<RevCommit> mergeLog = git.log().add(repo.resolve(branch)).not(repo.resolve("remotes/origin/" + compareBranch)).call();
                for (RevCommit commit : mergeLog) {
                    JsonObjectBuilder objBuilder = Json.createObjectBuilder();
                    objBuilder.add("commit", commit.getName().substring(0,7));
                    objBuilder.add("awaiting", "Merge " + branch + " into " + compareBranch);
                    objBuilder.add("branch", branch);
                    objBuilder.add("message", commit.getFullMessage());
                    objBuilder.add("author", commit.getAuthorIdent().getName());
                    objBuilder.add("date", dateFormat.format(commit.getAuthorIdent().getWhen()));
                    arrayBuilder.add(objBuilder.build());
                    rowCount++;
                }
            }

            if (rebase && !branch.equals("master") && !branch.equals("develop")) {
                Iterable<RevCommit> rebaseLog = git.log().add(repo.resolve(remote + compareBranch)).not(repo.resolve(branch)).call();
                for (RevCommit commit : rebaseLog) {
                        JsonObjectBuilder objBuilder = Json.createObjectBuilder();
                        objBuilder.add("commit", commit.getName().substring(0,7));
                        objBuilder.add("awaiting", "Rebase from " + compareBranch + " onto " + branch);
                        objBuilder.add("branch", remote + compareBranch);
                        objBuilder.add("message", commit.getFullMessage());
                        objBuilder.add("author", commit.getAuthorIdent().getName());
                        objBuilder.add("date", dateFormat.format(commit.getAuthorIdent().getWhen()));
                        arrayBuilder.add(objBuilder.build());
                        rowCount++;
                }
            }
            
            if (pull) {
                // Fix - handle case where the remote is something other than 'origin'
                ObjectId remoteObjId = repo.resolve(remoteTrackingBranch);
                Iterable<RevCommit> pullLog = git.log().add(remoteObjId).not(repo.resolve(branch)).call();
                String remoteBranch = remoteTrackingBranch.replace("refs/", "");
                for (RevCommit commit : pullLog) {
                    JsonObjectBuilder objBuilder = Json.createObjectBuilder();
                    objBuilder.add("commit", commit.getName().substring(0,7));
                    objBuilder.add("awaiting", "Pull from " + branch);
                            objBuilder.add("branch", remoteBranch);
                            objBuilder.add("message", commit.getFullMessage());
                            objBuilder.add("author", commit.getAuthorIdent().getName());
                            objBuilder.add("date", dateFormat.format(commit.getAuthorIdent().getWhen()));
                            arrayBuilder.add(objBuilder.build());
                            rowCount++;
                }
            }

            if (pull) { // Push
                // Fix - handle case where the remote is something other than 'origin'
                ObjectId remoteObjId = repo.resolve(remoteTrackingBranch);
                Iterable<RevCommit> pullLog = git.log().add(repo.resolve(branch)).not(remoteObjId).call();
                // String remoteBranch = remoteTrackingBranch.replace("refs/", "");
                for (RevCommit commit : pullLog) {
                    JsonObjectBuilder objBuilder = Json.createObjectBuilder();
                    objBuilder.add("commit", commit.getName().substring(0,7));
                    objBuilder.add("awaiting", "Push to repository");
                            objBuilder.add("branch", branch);
                            objBuilder.add("message", commit.getFullMessage());
                            objBuilder.add("author", commit.getAuthorIdent().getName());
                            objBuilder.add("date", dateFormat.format(commit.getAuthorIdent().getWhen()));
                            arrayBuilder.add(objBuilder.build());
                            rowCount++;
                }
            }

            JsonObject result = Json.createObjectBuilder().add("data", arrayBuilder.build())
                                                            .add("offset", 0)
                                                            .add("row_count", rowCount)
                                                            .add("title", branch).build();
            return result;
        } catch (IOException | GitAPIException e) {
            log.debug("Exception while getting the log: " + e.getMessage());
            throw new GitServiceException("Unable to get commits for branch: " + e.getMessage());
        }
        finally {
            if (git != null) {
                git.close();
            }
        }      
    }

    private String getRemote(String remoteTrackingBranch) {
        String result = "remotes/origin/";
        if (remoteTrackingBranch != null && remoteTrackingBranch.length() > 5 && 
            remoteTrackingBranch.lastIndexOf("/") > 5) {
                result =remoteTrackingBranch.substring(5, remoteTrackingBranch.lastIndexOf("/") + 1);
        }
        return result;   
    }

    /**
     * Gets the remote tracking branch for the current local branch.
     * 
     * @param workspace
     * @return Remote bracnh e.g. remotes/origin/develop
     */
    public String getTrackingBranch(String workspace) throws GitServiceException {
        Git git = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, workspace));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            String remoteBranch = new BranchConfig(repo.getConfig(), repo.getBranch()).getTrackingBranch();
            if (remoteBranch == null) {
                throw new GitServiceException("The remote tracking branch is not set up. Use <code>git branch -u remote/remote_branch</code> to set the remote tracking branch.");
            }
            return remoteBranch.replace("refs/", "");
        } catch (IOException e) {
            log.debug("Exception while getting remote tracking branch: " + e.getMessage());
            throw new GitServiceException("Unable to get remote tracking branch: " + e.getMessage());
        }
        finally {
            if (git != null) {
                git.close();
            }
        }      
    }

    /**
     * Gets the remote repository URI for the current local branch.
     * 
     * @param workspace
     * @return Remote bracnh e.g. git:/
     */
    public String getRemoteRepo(String workspace) throws GitServiceException {
        Git git = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, workspace));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);
            String remoteBranch = new BranchConfig(repo.getConfig(), repo.getBranch()).getRemote();
            List<RemoteConfig> remotesList = git.remoteList().call();
            for (RemoteConfig remote: remotesList) {
                for(URIish uri : remote.getURIs()){
                    if (remote.getName().equals(remoteBranch)) {
                        return uri.toString();
                    }
                }
            }
            return "unknown";
        } catch (IOException | GitAPIException e) {
            log.debug("Exception while getting remote repo URI: " + e.getMessage());
            throw new GitServiceException("Unable to get remote repo URI: " + e.getMessage());
        }
        finally {
            if (git != null) {
                git.close();
            }
        }      
    }

    private JsonObject convertStatusToJson(Status status, Collection<RevCommit> stashedRefs) {
        JsonObjectBuilder objBuilder = Json.createObjectBuilder();
        int changeCount = getChangesCount(status);
        objBuilder.add("id",  changeCount == 0 ? "git:status:/noChanges" : "git:status:/");
        objBuilder.add("name", format("Pending Changes (%s)", changeCount));
        objBuilder.add("tooltip", "");
        objBuilder.add("type", "branch");
        objBuilder.add("subType", changeCount == 0 ? "noChanges" : "changes");
        objBuilder.add("children", getStagedAndChanged(status, stashedRefs));
        return objBuilder.build();
    }

    private int getChangesCount(Status status) {
        int count = status.getChanged().size() + 
                    status.getAdded().size() + 
                    status.getRemoved().size() + 
                    status.getMissing().size() +
                    status.getModified().size() +
                    status.getUntracked().size() + 
                    status.getUntrackedFolders().size() + 
                    status.getConflicting().size();
        return count;
    }

    private JsonArray getStagedAndChanged(Status status, Collection<RevCommit> stashedRefs) {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        arrayBuilder.add(getStaged(status));
        arrayBuilder.add(getChanged(status));
        if (stashedRefs.size() > 0) {
            arrayBuilder.add(getStashList(stashedRefs));
        }
        return arrayBuilder.build();
    }

    private JsonObject getStaged(Status status) {
        JsonObjectBuilder objBuilder = Json.createObjectBuilder();
        objBuilder.add("id", "git:status:/Staged");
        objBuilder.add("name", "Staged");
        objBuilder.add("tooltip", "");
        objBuilder.add("type", "branch");
        objBuilder.add("subType", "stagedFolder");
        objBuilder.add("children", getStagedFiles(status));
        return objBuilder.build();
    }

    private JsonObject getChanged(Status status) {
        JsonObjectBuilder objBuilder = Json.createObjectBuilder();
        objBuilder.add("id", "git:status:/Changed");
        objBuilder.add("name", "Changed");
        objBuilder.add("tooltip", "");
        objBuilder.add("type", "branch");
        objBuilder.add("subType", "changedFolder");
        objBuilder.add("children", getChangedFiles(status));
        return objBuilder.build();
    }

    private JsonArray getStagedFiles(Status status) {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        appendFiles(status.getRemoved(), arrayBuilder, "gitStagedDeleted","[Staged for Removal]");
        appendFiles(status.getChanged(), arrayBuilder, "gitStaged", "[Staged]");
        appendFiles(status.getAdded(), arrayBuilder, "gitStaged", "[Added]");
        return arrayBuilder.build();
    }

    private JsonArray getChangedFiles(Status status) {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();   
        appendFiles(status.getMissing(), arrayBuilder, "gitChangedDeleted", "[Deleted]");
        appendFiles(status.getModified(), arrayBuilder, "gitChanged", "[Modified]");
        appendFiles(status.getUntracked(), arrayBuilder, "gitChanged", "[Untracked]");
        appendFiles(status.getUntrackedFolders(), arrayBuilder, "gitChanged", "[Untracked Folder]");
        appendFiles(status.getConflicting(), arrayBuilder, "gitConflicting", "[Conflicting]");
        // appendFiles(status.getIgnoredNotInIndex(), arrayBuilder);
        return arrayBuilder.build();
    }

    private void appendFiles(Set<String> set, JsonArrayBuilder arrayBuilder, String subType, String status) {
        for (String file : set) {
            JsonObjectBuilder objBuilder = Json.createObjectBuilder();
            if (subType.equals("gitChangedDeleted") || subType.equals("gitStagedDeleted")) {
                objBuilder.add("id", "git:delete:/" + file);
            } else {
                objBuilder.add("id", "file:/" + file);
            } 
            objBuilder.add("name", getFileNameFromPath(file));
            objBuilder.add("tooltip", file + " " + status);
            objBuilder.add("type", "leaf");
            objBuilder.add("subType", subType);
            arrayBuilder.add(objBuilder.build());
        }
    }

    private String getFileNameFromPath(String path) {
        int lastSlashPos = path.lastIndexOf('/');
        return (lastSlashPos >= 0 ? path.substring(lastSlashPos + 1) : path);
    }

    private JsonObject getStashList(Collection<RevCommit> stashedRefs) {
        JsonObjectBuilder objBuilder = Json.createObjectBuilder();
        objBuilder.add("id", "git:stashlist:/");
        objBuilder.add("name", "Stashes");
        objBuilder.add("tooltip", "");
        objBuilder.add("type", "branch");
        objBuilder.add("subType", "stashFolder");
        objBuilder.add("children", getStashes(stashedRefs));
        return objBuilder.build();
    }

    private JsonArray getStashes(Collection<RevCommit> stashedRefs) {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        for (RevCommit rev : stashedRefs)
        {   
            JsonObjectBuilder objBuilder = Json.createObjectBuilder();
            objBuilder.add("id", "git:stashfolder:/" + rev.getName());
            objBuilder.add("name", rev.getFullMessage());
            objBuilder.add("tooltip", "Stash");
            objBuilder.add("type", "leaf");
            objBuilder.add("subType", "stash");
            arrayBuilder.add(objBuilder.build());
        }
        return arrayBuilder.build();
    }

    /**
     * Performs a "git add <file>".
     * 
     * To add all changes, set file to "."
     * 
     * @param workspace
     * @param file File path, as returned by git status
     * @throws GitServiceException
     */
    public void add(String workspace, String file) throws GitServiceException {
        Git git = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, workspace));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);
            git.add().addFilepattern(file).call();

        } catch (IOException | GitAPIException e) {
            throw new GitServiceException(format("Unable to add file %s to workspace %s", file, workspace), e);
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

   /**
     * Perform a "git rebase --continue"
     * 
     * 
     * @param branch
     * @param file File path, as returned by git status
     * @throws GitServiceException
     */
    public void rebaseContinue(String branch) throws GitServiceException {
        Git git = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, branch));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);
            git.rebase().setOperation(Operation.CONTINUE).call();

        } catch (IOException | GitAPIException e) {
            throw new GitServiceException("Unable to perform a git rebase --continue", e);
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }



    /**
     * Performs a "git rm <file>" to stage a file removal or deletion.
     * 
     * @param branch
     * @param file File path, as returned by git status
     * @throws GitServiceException
     */
    public void rm(String workspace, String file) throws GitServiceException {
        Git git = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, workspace));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);
            git.rm().addFilepattern(file).call();

        } catch (IOException | GitAPIException e) {
            throw new GitServiceException(format("Unable to remove file %s from workspace %s", file, workspace), e);
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

    /**
     * Performs a "git restore <file>" to restore a file and discard changes.
     * 
     * @param workspace
     * @param file File path, as returned by git status
     * @throws GitServiceException
     */
    public void restore(String workspace, String file) throws GitServiceException {
        Git git = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, workspace));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);

            git.checkout().addPath(file).call();

        } catch (IOException | GitAPIException e) {
            throw new GitServiceException(format("Unable to restore file %s to workspace %s", file, workspace), e);
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }




    /**
     * Performs a "git restore --staged  <file>" to unstage a file.
     * 
     * @param branch
     * @param file File path, as returned by git status
     * @throws GitServiceException
     */
    public void unstage(String branch, String file) throws GitServiceException {
        Git git = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, branch));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);
            git.reset().addPath(file).call();
        } catch (IOException | GitAPIException e) {
            throw new GitServiceException(format("Unable to unstage file %s on branch %s", file, branch), e);
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

    /**
     * Performs a "git commit -m  <message>" to commit the staged changes
     * followed by a "git push".                   
     * 
     * @param branch
     * @param file File path, as returned by git status
     * @throws GitServiceException
     */
    public void commit(String branch, String message, String authorName, String authorEmail) throws GitServiceException {
        Git git = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, branch));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);

            // See if there are any staged files
            Status status = git.status().call();
            if (status.getChanged().size() + status.getAdded().size() + status.getRemoved().size() == 0 ) {
                // If there are no Staged files, stage any Changed files first.
                git.add().addFilepattern(".").call();
                git.add().setUpdate(true).addFilepattern(".").call();
                status = git.status().call();
                if (status.getChanged().size() + status.getAdded().size() + status.getRemoved().size() == 0) {
                    log.info("Commit attempted when there are no Staged or Changed files");
                    return;
                }
            }
            try {
                git.commit().setAuthor(authorName, authorEmail).setMessage(message).call();
            } catch (GitAPIException e) {
                // Try doing a git rebase --continue is case we're in the middle of resolving conflicts.
                log.warn("Git exception with attempting commit. Will try doing a git rebase --continue first.");
                git.rebase().setOperation(Operation.CONTINUE).call();
                git.commit().setMessage(message).call();
            }
            
            git.push().call();
           
        } catch (IOException | GitAPIException e) {
            if (!e.getMessage().contains("Nothing to push")) {
                throw new GitServiceException(format("Unable to perform a commit on branch %s, message %s", branch, message), e);
            }
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }


   /**
     * Performs a "git stash" to stash changes.
     *   
     * @param branch
     * @param file File path, as returned by git status
     * @throws GitServiceException
     */
    public void stash(String branch, String message) throws GitServiceException {
        Git git = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, branch));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);

            git.stashCreate().setIncludeUntracked(true).setWorkingDirectoryMessage(message).call();
            
        } catch (IOException | GitAPIException e) {
            throw new GitServiceException(format("Unable to a stash changes on branch %s, message %s", branch, message), e);
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

   /**
     * Performs a "git stash pop" to restore a stash.
     *   
     * @param branch
     * @param stashId The stash id.
     * @throws GitServiceException
     */
    public void stashPop(String branch, String stashRef) throws GitServiceException {
        Git git = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, branch));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);
            git.stashApply().setStashRef(stashRef).call();
            stashDrop(branch, stashRef);
            
        } catch (IOException | GitAPIException e) {
            throw new GitServiceException(format("Unable to restore stash %s on branch %s. %s", stashRef, branch, e.getMessage()), e);
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

   /**
     * Performs a "git stash drop" to delete a stash.
     *   
     * @param branch
     * @param stashId The stash id.
     * @throws GitServiceException
     */
    public void stashDrop(String branch, String stashRef) throws GitServiceException {
        Git git = null;
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, branch));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);

            StashListCommand stashList = git.stashList();
            Collection<RevCommit> stashedRefs = stashList.call();
            int stashIndex = getStashIndex(stashedRefs, stashRef);
            git.stashDrop().setStashRef(stashIndex).call();
            
        } catch (IOException | GitAPIException e) {
            throw new GitServiceException(format("Unable to delete stash %s on branch %s", stashRef, branch), e);
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

    private int getStashIndex(Collection<RevCommit> stashedRefs, String stashRef) throws GitServiceException {
        int stashIndex = 0;
        for (RevCommit rev : stashedRefs) {
            if (rev.getName().equals(stashRef)) {
                return stashIndex;
            }
        }
        throw new GitServiceException(format("Can't find stash %s",  stashRef));
    }

    /**
     * Stages any changes on the branch, commits and pushes them to the remote repository.
     * 
     * @param branch
     * @param appName
     * @param fileName
     * @param message
     * @throws GitServiceException
     */
    public synchronized void stageCommitPushChange(String branch, String appName, String fileName,
            String message) throws GitServiceException {
        Git git = null;
        String filePattern = format("%s%s", appName, fileName);
        try {
            Path repoPath = Paths.get(format("%s/%s", localRepoDir, branch));
            Repository repo = new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile()).build();
            git = new Git(repo);
            git.add().addFilepattern(filePattern).call();
            git.commit().setMessage(message).call();
            git.push().call();
        } catch (IOException | GitAPIException e) {
            throw new GitServiceException(format("Unable to commit change for %s branch %s", filePattern, branch), e);
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

    /**
     * Merges the develop branch into master. A merge should first be attempted using forceMerge set to false.
     * This results in a merge using the RECURSIVE merge strategy. If the merge failed because of conflicts, it
     * can be forced by setting forceMerge to true. This will result in merge conflicts in master getting
     * overwritten by the changes on develop. Provided changes are never made on the master brnach, there
     * shouldn't be any merge conflicts.
     * 
     * @param forceMerge When true, forces develop changes onto master. May result in loss of changes on master.
     * @return Returns true if the merge was a success.
     * @throws GitServiceException
     */
    public synchronized boolean mergeDevelopIntoMaster(boolean forceMerge) throws GitServiceException {
        Git git = null;
        try {
            MergeStrategy mergeStrategy = forceMerge ? MergeStrategy.THEIRS : MergeStrategy.RECURSIVE;
            Path masterRepoPath = Paths.get(format("%s/%s", localRepoDir, PRODUCTION_WORKSPACE));
            Repository masterRepo = new FileRepositoryBuilder().setGitDir(masterRepoPath.resolve(".git").toFile()).build();

            git = new Git(masterRepo);
            git.pull().call();
      
            Ref ref = git.getRepository().findRef("origin/develop");
            
            MergeResult merge = git.merge().
                include(ref).
                setCommit(true).
                setStrategy(mergeStrategy).
                setFastForward(MergeCommand.FastForwardMode.NO_FF).
                setMessage("Merged develop into master").
                call();

            log.debug("Merge-Results for id: " + ref + ": " + merge);

            if (merge.getConflicts() != null) {
                log.error("Merge of develop into master failed due to merge conflicts.");
                for (Map.Entry<String,int[][]> entry : merge.getConflicts().entrySet()) {
                    log.error("Key: " + entry.getKey());
                    for(int[] arr : entry.getValue()) {
                        log.error("value: " + Arrays.toString(arr));
                    }
                }
                return false;
            }

            git.push().call();
            return true;

        } catch (Exception e) {
            throw new GitServiceException("Merge of develop branch into master failed:", e);
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

    /**
     * Tags the head of master with a new version number. Checks the existing tags and creates
     * a new one that's one higher. Tags are of the format v<major number>.<minor number> e.g. v1.2
     * 
     * @param majorRelease If true, the major part of the version number is increased.
     * @return The tag created.
     * @throws GitServiceException
    */
    public synchronized String tagMaster(boolean majorRelease) throws GitServiceException {
        Git git = null;
        String releaseTag = "";
        try {
            Path masterRepoPath = Paths.get(format("%s/%s", localRepoDir, PRODUCTION_WORKSPACE));
            Repository masterRepo = new FileRepositoryBuilder().setGitDir(masterRepoPath.resolve(".git").toFile()).build();
            git = new Git(masterRepo);

            // Find latest tag.
            List<Ref> call = git.tagList().call();
            int highestMajor = 0;
            int highestMinor = 1;
            for (Ref ref : call) {
                String numsplit = ref.getName().replaceAll("[^0-9]+", " ").trim();
                String[] nums = numsplit.split(" ");
                int major = Integer.parseInt(nums[0]);
                int minor = Integer.parseInt(nums[1]);
                if (major > highestMajor || (major == highestMajor && minor > highestMinor)) {
                        highestMajor = major;
                        highestMinor = minor;
                }
            }

            int releaseMajor = majorRelease ? highestMajor + 1 : highestMajor;
            int releaseMinor = majorRelease ? 0 : highestMinor + 1;
            releaseTag = format("v%s.%s", releaseMajor, releaseMinor);
            String releaseTagMsg = format("Version %s.%s", releaseMajor, releaseMinor);
            git.tag().setName(releaseTag).setMessage(releaseTagMsg).call();    
            git.push().setPushTags().call();

        } catch (Exception e) {
            throw new GitServiceException("Tag of master branch failed:", e);
        } finally {
            if (git != null) {
                git.close();
            }
        }            
        return releaseTag;
    }
}


/**
 * Makes lower case items come before upper case items
 */
class Sort implements Comparator<String> {
    public int compare(String a, String b) {
        if (a.charAt(0) == Character.toLowerCase(a.charAt(0)) && (b.charAt(0) == Character.toUpperCase(b.charAt(0)))) {
            return -1;
        }
        if (a.charAt(0) == Character.toUpperCase(a.charAt(0)) && (b.charAt(0) == Character.toLowerCase(b.charAt(0)))) {
            return 1;
        }
        return a.compareTo(b);
    }
}