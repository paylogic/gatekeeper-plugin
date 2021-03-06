package org.paylogic.jenkins.advancedscm;

import org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException;
import org.paylogic.jenkins.upmerge.releasebranch.ReleaseBranch;
import org.paylogic.jenkins.upmerge.releasebranch.ReleaseBranchInvalidException;

import java.util.List;

public interface AdvancedSCMManager {

    /**
     * Get Mercurial branches from command line output,
     * and put them in a List with Branches so it's nice to work with.
     * @param all : get all or only open branches
     * @return List of Branches
     */
    public List<Branch> getBranches (boolean all) throws AdvancedSCMException;

    /**
     * Get open Mercurial branches from command line output,
     * and put them in a List so it's nice to work with.
     * @param all : get all or only open branches
     * @return List of String
     */
    public List<String> getBranchNames(boolean all) throws AdvancedSCMException;

    /**
     * Get the current branch name in the workspace.
     * @return String with branch name in it.
     */
    public String getBranch() throws AdvancedSCMException;

    /**
     * Updates workspace to given revision/branch.
     * @param revision : String with revision, hash of branchname to update to.
     */
    public void update(String revision) throws AdvancedSCMException;

    /**
     * Updates workspace to given revision/branch with cleaning.
     * @param revision : String with revision, hash of branchname to update to.
     */
    public void updateClean(String revision) throws AdvancedSCMException;

    /**
     * Strip out local commits which are not pushed yet.
     */
    public void stripLocal() throws AdvancedSCMException;

    /**
     * Cleans workspace from artifacts.
     */
    public void clean() throws AdvancedSCMException;

    /**
     * Merge current workspace with given revision.
     * @param revision : String with revision, hash or branchname to merge with.
     * @param updateTo : String with revision, hash or branchname to update working copy to before actual merge.
     */
    public void mergeWorkspaceWith(String revision, String updateTo) throws AdvancedSCMException;

    /**
     * Commit current workspace.
    * @param message : String commit message
    * @param username : String commit user name (with email)
    */
    public void commit(String message, String username) throws AdvancedSCMException;

    /**
     * Merge possible current branch's heads.
     * @param message : String commit message
     * @param username : String commit user name (with email)
     */
    public void mergeHeads(String message, String username) throws AdvancedSCMException;


    /**
     * Close given branch
     * @param branch: String branch name.
     * @param message : String with message to give this commit.
     * @param username : String commit user name (with email)
     */
    public void closeBranch(String branch, String message, String username) throws AdvancedSCMException;

    /**
     * Executes 'push' command with -b <branch>
     */
    public void push(String... branchNames) throws AdvancedSCMException;

    /**
     * Executes 'pull' command
     * @throws org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException
     */
    public void pull() throws AdvancedSCMException;

    /**
     * Pulls changes from remotes. Give it a remote to pull changes from there.
     * @throws org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException
     */
    public void pull(String remote) throws AdvancedSCMException;

    /**
     * Pulls from given repository url. Give it a remote to pull changes from there.
     * @throws org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException
     */
    public void pull(String remote, String branch) throws AdvancedSCMException;

    /**
     * Get release branch from given branch name.
     * @param branch : String branch name
     * @return ReleaseBranch : release branch object
     */
    public ReleaseBranch getReleaseBranch(String branch) throws ReleaseBranchInvalidException;

    /**
     * Ensure the release branch exists.
     * @param branch : String branch name
     * @param releaseFilePath: String relative path of the release file
     * @param releaseFileContent : String release file content
     * @param message: String commit message to add release file
     * @param username: String username of the commit commit
     * @throws org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException
     */
    public void ensureReleaseBranch(
            String branch, String releaseFilePath, String releaseFileContent, String message, String username)
            throws AdvancedSCMException, ReleaseBranchInvalidException;

    /**
     * Create the release branch.
     * @param branch : String branch name
     * @param releaseFilePath: String relative path of the release file
     * @param releaseFileContent : String release file content
     * @param message: String commit message to add release file
     * @param username: String username of the commit commit
     * @throws org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException
     */
    public ReleaseBranch createReleaseBranch(
            String branch, String releaseFilePath, String releaseFileContent, String message, String username)
            throws AdvancedSCMException, ReleaseBranchInvalidException;
}
