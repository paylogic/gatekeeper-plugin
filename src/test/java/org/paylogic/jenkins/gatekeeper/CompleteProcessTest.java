package org.paylogic.jenkins.gatekeeper;

import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.mercurial.MercurialSCM;
import lombok.extern.java.Log;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.paylogic.jenkins.advancedscm.GitRule;
import org.paylogic.jenkins.advancedscm.MercurialRule;
import org.paylogic.jenkins.upmerge.UpmergeBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@Log
public class CompleteProcessTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public MercurialRule m = new MercurialRule(j);
    @Rule public GitRule g = new GitRule(j);
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    @Rule public TemporaryFolder tmp2 = new TemporaryFolder();
    private File repo;
    private File repo2;

    @Before
    public void setUp() throws Exception {
        repo = tmp.getRoot();
        repo2 = tmp2.getRoot();
    }

    @Test
    public void testGatekeeperingAndUpmergingMercurial() throws Exception {
        /*
         * So:
         * set up a repo with 3 releases and 1 feature branch
         * inject parameters TARGET_BRANCH and stuff
         * run job with gatekeeper, upmerge and push tasks
         * assert file from feature branch is in latest release branch
         */
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(null, repo.getPath(), "tip", null, null, null, false));

        // Init repo with 3 releases and feature branch.
        m.hg(repo, "init");
        m.touchAndCommit(repo, "base");
        m.hg(repo, "branch", "r1336");
        m.touchAndCommit(repo, "r1336");
        m.hg(repo, "branch", "r1338");
        m.touchAndCommit(repo, "r1338");
        m.hg(repo, "branch", "r1340");
        m.touchAndCommit(repo, "r1340");
        m.hg(repo, "update", "r1336");
        m.hg(repo, "branch", "c3");
        m.touchAndCommit(repo, "c3");

        GatekeeperMerge mergeBuilder = new GatekeeperMerge("JenkinsTestRunner <test@runner.com>", null, null);
        UpmergeBuilder upmergeBuilder = new UpmergeBuilder("JenkinsTestRunner <test@runner.com>");
        GatekeeperPush pushBuilder = new GatekeeperPush();

        ArrayList<ParameterValue> parameters = new ArrayList<ParameterValue>();
        parameters.add(new StringParameterValue("TARGET_BRANCH", "r1336"));
        parameters.add(new StringParameterValue("ORIGINAL_BRANCH", "r1336"));
        parameters.add(new StringParameterValue("FEATURE_BRANCH", "c3"));

        p.getBuildersList().add(mergeBuilder);
        p.getBuildersList().add(upmergeBuilder);
        p.getBuildersList().add(pushBuilder);
        m.buildAndCheck(p, "c3", new ParametersAction(parameters));

        // Check more files, we can do this on original repo, so we make sure that builder pushed changes.
        m.hg(repo, "update", "r1336");
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();

        m.hg(repo, "update", "r1338");
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();
        assert new File(repo, "r1338").exists();

        m.hg(repo, "update", "r1340");
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();
        assert new File(repo, "r1338").exists();
        assert new File(repo, "r1340").exists();

        m.hg(repo, "update", "default");
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();
        assert new File(repo, "r1338").exists();
        assert new File(repo, "r1340").exists();

        assert !m.searchLog(repo, "[Jenkins Integration Merge] Merged c3 into r1336").isEmpty();
        assert !m.searchLog(repo, "[Jenkins Upmerging] Merged r1336 into r1338").isEmpty();
        assert !m.searchLog(repo, "[Jenkins Upmerging] Merged r1338 into r1340").isEmpty();

        //check that c3 feature branch is closed
        assertArrayEquals(new String[] {"default", "r1336", "r1338", "r1340"}, m.getBranches(repo));
    }

    @Test
    public void testGatekeeperingAndUpmergingGit() throws Exception {
        /*
         * So:
         * set up a repo with 3 releases and 1 feature branch
         * inject parameters TARGET_BRANCH and stuff
         * run job with gatekeeper, upmerge and push tasks
         * assert file from feature branch is in latest release branch
         */
        FreeStyleProject p = j.createFreeStyleProject();
        List<UserRemoteConfig> remotes = new ArrayList<UserRemoteConfig>();
        remotes.add(new UserRemoteConfig(repo.getPath(), "origin", "master", null));
        List<BranchSpec> branches = new ArrayList<BranchSpec>();
        branches.add(new BranchSpec("master"));
        p.setScm(new GitSCM(remotes, branches, false, null, null, null, null));

        // Init repo with 3 releases and feature branch.
        GitClient client = g.gitClient(repo);
        client.init();
        g.touchAndCommit(repo, "base");
        client.checkout("HEAD", "r1336");
        g.touchAndCommit(repo, "r1336");
        client.checkout("HEAD", "r1338");
        g.touchAndCommit(repo, "r1338");
        client.checkout("HEAD", "r1340");
        g.touchAndCommit(repo, "r1340");
        client.checkout().branch("r1336");
        client.checkout("HEAD", "c3");
        g.touchAndCommit(repo, "c3");

        GatekeeperMerge mergeBuilder = new GatekeeperMerge("JenkinsTestRunner <test@runner.com>", null, null);
        UpmergeBuilder upmergeBuilder = new UpmergeBuilder("JenkinsTestRunner <test@runner.com>");
        GatekeeperPush pushBuilder = new GatekeeperPush();

        ArrayList<ParameterValue> parameters = new ArrayList<ParameterValue>();
        parameters.add(new StringParameterValue("TARGET_BRANCH", "r1336"));
        parameters.add(new StringParameterValue("ORIGINAL_BRANCH", "r1336"));
        parameters.add(new StringParameterValue("FEATURE_BRANCH", "c3"));

        p.getBuildersList().add(mergeBuilder);
        p.getBuildersList().add(upmergeBuilder);
        p.getBuildersList().add(pushBuilder);
        g.buildAndCheck(p, "c3", new ParametersAction(parameters));

        // Check more files, we can do this on original repo, so we make sure that builder pushed changes.
        client.checkout().ref("r1336").execute();
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();

        client.checkout().ref("r1338").execute();
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();
        assert new File(repo, "r1338").exists();

        client.checkout().ref("r1340").execute();
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();
        assert new File(repo, "r1338").exists();
        assert new File(repo, "r1340").exists();

        client.checkout().ref("master").execute();
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();
        assert new File(repo, "r1338").exists();
        assert new File(repo, "r1340").exists();

        //check that c3 feature branch is not removed
        assertArrayEquals(new String[]{"c3", "master", "r1336", "r1338", "r1340"}, g.getBranches(repo));
    }

    @Test
    public void testGatekeeperingNewReleaseBranchMercurial() throws Exception {
        /*
         * So:
         * set up a repo with 1 release and 1 feature branches
         * inject parameters TARGET_BRANCH and stuff
         * run job with gatekeeper, upmerge and push tasks
         * assert file from feature branch is in latest release branch
         */
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(null, repo.getPath(), "tip", null, null, null, false));

        // Init repo with 1 release and 1 feature branches.
        m.hg(repo, "init");
        m.touchAndCommit(repo, "base");
        m.hg(repo, "branch", "r1336");
        m.touchAndCommit(repo, "r1336");
        m.hg(repo, "branch", "c3");
        m.touchAndCommit(repo, "c3");

        GatekeeperMerge mergeBuilder = new GatekeeperMerge("JenkinsTestRunner <test@runner.com>", "release.txt", "{{release}}");
        UpmergeBuilder upmergeBuilder = new UpmergeBuilder("JenkinsTestRunner <test@runner.com>");
        GatekeeperPush pushBuilder = new GatekeeperPush();

        ArrayList<ParameterValue> parameters = new ArrayList<ParameterValue>();
        parameters.add(new StringParameterValue("TARGET_BRANCH", "r1338"));
        parameters.add(new StringParameterValue("ORIGINAL_BRANCH", "r1336"));
        parameters.add(new StringParameterValue("FEATURE_BRANCH", "c3"));

        p.getBuildersList().add(mergeBuilder);
        p.getBuildersList().add(upmergeBuilder);
        p.getBuildersList().add(pushBuilder);
        m.buildAndCheck(p, "c3", new ParametersAction(parameters));

        // Check more files, we can do this on original repo, so we make sure that builder pushed changes.
        m.hg(repo, "update", "r1338");
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();
        File release = new File(repo, "release.txt");
        assert release.exists();
        FileInputStream inputStream = new FileInputStream(release);
        try {
            String contents = IOUtils.toString(inputStream);
            assertEquals(contents, "1338");
        } finally {
            inputStream.close();
        }

        m.hg(repo, "update", "default");
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();

        assert !m.searchLog(repo, "[Jenkins Integration Merge] Merged c3 into r1338").isEmpty();
        assert !m.searchLog(repo, "[Jenkins Upmerging] Merged r1338 into default").isEmpty();

        //check that c3 feature branch is closed
        assertArrayEquals(new String[] {"default", "r1336", "r1338"}, m.getBranches(repo));
    }

    @Test
    public void testGatekeeperingNewReleaseBranchGit() throws Exception {
        /*
         * So:
         * set up a repo with 1 release and 1 feature branches
         * inject parameters TARGET_BRANCH and stuff
         * run job with gatekeeper, upmerge and push tasks
         * assert file from feature branch is in latest release branch
         */
        FreeStyleProject p = j.createFreeStyleProject();
        List<UserRemoteConfig> remotes = new ArrayList<UserRemoteConfig>();
        remotes.add(new UserRemoteConfig(repo.getPath(), "origin", "master", null));
        List<BranchSpec> branches = new ArrayList<BranchSpec>();
        branches.add(new BranchSpec("master"));
        p.setScm(new GitSCM(remotes, branches, false, null, null, null, null));

        // Init repo with 1 release and 1 feature branches.
        GitClient client = g.gitClient(repo);
        client.init();
        g.touchAndCommit(repo, "base");
        client.checkout("HEAD", "r1336");
        g.touchAndCommit(repo, "r1336");
        client.checkout("HEAD", "c3");
        g.touchAndCommit(repo, "c3");

        GatekeeperMerge mergeBuilder = new GatekeeperMerge("JenkinsTestRunner <test@runner.com>", "release.txt", "{{release}}");
        UpmergeBuilder upmergeBuilder = new UpmergeBuilder("JenkinsTestRunner <test@runner.com>");
        GatekeeperPush pushBuilder = new GatekeeperPush();

        ArrayList<ParameterValue> parameters = new ArrayList<ParameterValue>();
        parameters.add(new StringParameterValue("TARGET_BRANCH", "r1338"));
        parameters.add(new StringParameterValue("ORIGINAL_BRANCH", "r1336"));
        parameters.add(new StringParameterValue("FEATURE_BRANCH", "c3"));

        p.getBuildersList().add(mergeBuilder);
        p.getBuildersList().add(upmergeBuilder);
        p.getBuildersList().add(pushBuilder);
        g.buildAndCheck(p, "c3", new ParametersAction(parameters));

        // Check more files, we can do this on original repo, so we make sure that builder pushed changes.
        client.checkout().ref("r1338").execute();
        client.clean();
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();
        File release = new File(repo, "release.txt");
        assert release.exists();
        FileInputStream inputStream = new FileInputStream(release);
        try {
            String contents = IOUtils.toString(inputStream);
            assertEquals(contents, "1338");
        } finally {
            inputStream.close();
        }

        client.checkout().ref("master").execute();
        client.clean();
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();

        assert !g.searchLog(repo, "[Jenkins Integration Merge] Merged c3 into r1338").isEmpty();
        assert !g.searchLog(repo, "[Jenkins Upmerging] Merged r1338 into master").isEmpty();
    }

    @Test
    public void testGatekeeperingFromDifferentRepoAndUpmergingMercurial() throws Exception {
        /*
         * So:
         * set up a repo with 3 releases
         * set up another repo which should somehow be a copy of this repo with a feature branch
         * inject APPROVED_REVISION, which is the latest revision of the feature brnach
         * inject parameters TARGET_BRANCH and stuff
         * run job with both gatekeeper and upmerge tasks
         * assert file from feature branch is in latest release branch
         */

        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(null, repo.getPath(), "tip", null, null, null, false));

        // Init repo with 3 releases and feature branch.
        m.hg(repo, "init");
        m.touchAndCommit(repo, "base");
        m.hg(repo, "branch", "r1336");
        m.touchAndCommit(repo, "r1336");
        m.hg(repo, "branch", "r1338");
        m.touchAndCommit(repo, "r1338");
        m.hg(repo, "branch", "r1340");
        m.touchAndCommit(repo, "r1340");

        // Clone to second repo
        m.hg(repo2, "clone", repo.getAbsolutePath(), ".");
        m.hg(repo2, "update", "r1336");
        m.hg(repo2, "branch", "c3");
        m.touchAndCommit(repo2, "c3");

        final String okRevision = m.getLastChangesetId(repo2);
        ArrayList<ParameterValue> parameters = new ArrayList<ParameterValue>();
        parameters.add(new StringParameterValue("TARGET_BRANCH", "r1336"));
        parameters.add(new StringParameterValue("ORIGINAL_BRANCH", "r1336"));
        parameters.add(new StringParameterValue("FEATURE_BRANCH", "c3"));
        parameters.add(new StringParameterValue("APPROVED_REVISION", okRevision));
        parameters.add(new StringParameterValue("REPO_URL", repo2.getAbsolutePath()));

        p.getBuildersList().add(new GatekeeperMerge("JenkinsTestRunner <test@runner.com>", null, null));
        p.getBuildersList().add(new UpmergeBuilder("JenkinsTestRunner <test@runner.com>"));
        p.getBuildersList().add(new GatekeeperPush());

        m.buildAndCheck(p, "c3", new ParametersAction(parameters));

        // Check more files, we can do this on original repo, so we make sure that builder pushed changes.
        m.hg(repo, "update", "r1336");
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();

        m.hg(repo, "update", "r1338");
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();
        assert new File(repo, "r1338").exists();

        m.hg(repo, "update", "r1340");
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();
        assert new File(repo, "r1338").exists();
        assert new File(repo, "r1340").exists();

        m.hg(repo, "update", "default");
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();
        assert new File(repo, "r1338").exists();
        assert new File(repo, "r1340").exists();

        assert !m.searchLog(repo, "[Jenkins Integration Merge] Merged c3 into r1336").isEmpty();
        assert !m.searchLog(repo, "[Jenkins Upmerging] Merged r1336 into r1338").isEmpty();
    }

    @Test
    public void testGatekeeperingFromDifferentRepoAndUpmergingGit() throws Exception {
        /*
         * So:
         * set up a repo with 3 releases
         * set up another repo which should somehow be a copy of this repo with a feature branch
         * inject APPROVED_REVISION, which is the latest revision of the feature brnach
         * inject parameters TARGET_BRANCH and stuff
         * run job with both gatekeeper and upmerge tasks
         * assert file from feature branch is in latest release branch
         */

        FreeStyleProject p = j.createFreeStyleProject();
        List<UserRemoteConfig> remotes = new ArrayList<UserRemoteConfig>();
        remotes.add(new UserRemoteConfig(repo.getPath(), "origin", "master", null));
        List<BranchSpec> branches = new ArrayList<BranchSpec>();
        branches.add(new BranchSpec("master"));
        p.setScm(new GitSCM(remotes, branches, false, null, null, null, null));

        // Init repo with 3 releases and feature branch.
        GitClient client = g.gitClient(repo);
        client.init();
        g.allowPush(client);
        g.touchAndCommit(repo, "base");
        client.checkout().branch("r1336").execute();
        g.touchAndCommit(repo, "r1336");
        client.checkout().branch("r1338").execute();
        g.touchAndCommit(repo, "r1338");
        client.checkout().branch("r1340").execute();
        g.touchAndCommit(repo, "r1340");

        GitClient client2 = g.gitClient(repo2);
        client2.clone(repo.getAbsolutePath(), "origin", false, null);
        client2.checkout().branch("r1336").execute();
        client2.checkout().branch("c3").execute();
        g.touchAndCommit(repo2, "c3");

        final String okRevision = g.getLastChangesetId(repo2);
        ArrayList<ParameterValue> parameters = new ArrayList<ParameterValue>();
        parameters.add(new StringParameterValue("TARGET_BRANCH", "r1336"));
        parameters.add(new StringParameterValue("ORIGINAL_BRANCH", "r1336"));
        parameters.add(new StringParameterValue("FEATURE_BRANCH", "c3"));
        parameters.add(new StringParameterValue("APPROVED_REVISION", okRevision));
        parameters.add(new StringParameterValue("REPO_URL", repo2.getAbsolutePath()));

        p.getBuildersList().add(new GatekeeperMerge("JenkinsTestRunner <test@runner.com>", null, null));
        p.getBuildersList().add(new UpmergeBuilder("JenkinsTestRunner <test@runner.com>"));
        p.getBuildersList().add(new GatekeeperPush());

        g.buildAndCheck(p, "c3", new ParametersAction(parameters));

        // Check more files, we can do this on original repo, so we make sure that builder pushed changes.
        client.checkout().ref("r1336").execute();
        client.clean();
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();

        client.checkout().ref("r1338").execute();
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();
        assert new File(repo, "r1338").exists();

        client.checkout().ref("r1340").execute();
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();
        assert new File(repo, "r1338").exists();
        assert new File(repo, "r1340").exists();

        client.checkout().ref("master").execute();
        assert new File(repo, "c3").exists();
        assert new File(repo, "r1336").exists();
        assert new File(repo, "r1338").exists();
        assert new File(repo, "r1340").exists();
        assert !g.searchLog(repo, "[Jenkins Integration Merge] Merged c3 into r1336").isEmpty();
        assert !g.searchLog(repo, "[Jenkins Upmerging] Merged r1336 into r1338").isEmpty();
    }

}
