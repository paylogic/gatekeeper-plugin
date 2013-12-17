package org.paylogic.jenkins.gatekeeper;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import lombok.Getter;
import lombok.extern.java.Log;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.paylogic.fogbugz.FogbugzCase;
import org.paylogic.jenkins.advancedmercurial.AdvancedMercurialManager;
import org.paylogic.jenkins.fogbugz.FogbugzNotifier;
import org.paylogic.redis.RedisProvider;
import redis.clients.jedis.Jedis;

import javax.swing.text.html.HTML;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;


/**
 * Main extension of the GatekeeperPlugin. Does Gatekeepering.
 */
@Log
public class GatekeeperPlugin extends Builder {

    @Getter private final boolean doGatekeeping;

    @DataBoundConstructor
    public GatekeeperPlugin(boolean doGatekeeping) {
        this.doGatekeeping = doGatekeeping;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        PrintStream l = listener.getLogger();
        l.println("----------------------------------------------------------");
        l.println("------------------- Now Gatekeepering --------------------");
        l.println("----------------------------------------------------------");
        try {
            return this.doPerform(build, launcher, listener);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception during Gatekeeepring.", e);
            l.append("Exception occured, build aborting...");
            l.append(e.toString());
            return false;
        }
    }

    private boolean doPerform(AbstractBuild build, Launcher launcher, BuildListener listener) throws Exception {
        /* Set up enviroment and resolve some variables. */
        EnvVars envVars = build.getEnvironment(listener);
        String givenCaseId = Util.replaceMacro("$CASE_ID", envVars);
        int usableCaseId = Integer.parseInt(givenCaseId);

        AdvancedMercurialManager amm = new AdvancedMercurialManager(build, launcher, listener);

        /* Fetch branch information from Fogbugz */
        FogbugzCase fallbackCase = new FogbugzNotifier().getFogbugzCaseManager().getCaseById(usableCaseId);
        String repo_path = fallbackCase.getFeatureBranch().split("#")[0];
        String featureBranch = fallbackCase.getFeatureBranch().split("#")[1];
        String targetBranch = fallbackCase.getTargetBranch();

        /* Actual Gatekeepering logic. Seperated to work differently when Rietveld support is active. */
        boolean runNormalMerge = this.getDescriptor().getUrl().isEmpty();
        if (runNormalMerge) {
            // Use Rietveld support.
            String featureRepoUrl = this.getDescriptor().getRepoBase() + repo_path;  // Should produce correct url.

            URL uri = new URL(this.getDescriptor().getUrl() + "/get_latest_ok_for_case/" + Integer.toString(usableCaseId)  + "/");
            HttpURLConnection con = (HttpURLConnection) uri.openConnection();
            if (con.getResponseCode() != 200) {
                log.log(Level.SEVERE, "Error while fetching latest OK revision from Rietveld.");
                return false;
            }

            String okRevision = con.getInputStream().toString();
            amm.pull(featureRepoUrl);
            amm.update(targetBranch);
            amm.mergeWorkspaceWith(okRevision);
        }

        if (!runNormalMerge) {  // We check twice, so on failure we should be able to resume normally (not in use atm.)
            amm.pull();
            amm.update(targetBranch);
            amm.mergeWorkspaceWith(featureBranch);
        }


        amm.commit("[Jenkins Integration Merge] Merge " + targetBranch + " with " + featureBranch);

        /* Set the featureBranch we merged with in redis to other plugin know this was actually the build's branch */
        RedisProvider redisProvider = new RedisProvider();
        Jedis redis = redisProvider.getConnection();
        redis.set("old_" + build.getExternalizableId(), featureBranch);

        /* Add commit to list of things to push. */
        redis.rpush("topush_" + build.getExternalizableId(), targetBranch);
        redisProvider.returnConnection(redis);
        return true;
    }


    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private String url;
        private String repoBase;

        public String getUrl() {
            if (this.url == null) {
                return "";
            } else {
                return this.url;
            }
        }

        public void setUrl(String url) {
            if (url == null) {
                this.url = "";
            } else {
                this.url = url;
            }
        }

        public String getRepoBase() {
            if (this.repoBase == null) {
                return "";
            } else {
                return this.repoBase;
            }
        }

        public void setRepoBase(String repoBase){
            if (repoBase == null) {
                this.repoBase = "";
            } else {
                this.repoBase = repoBase;
            }
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Perform Gatekeerping.";
        }

        public DescriptorImpl() {
            super();
            load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            this.setUrl(formData.getString("url"));
            this.setRepoBase(formData.getString("repoBase"));
            save();
            return super.configure(req, formData);
        }
    }
}

