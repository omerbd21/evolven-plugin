package io.jenkins.plugins.sample;

import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.util.FormValidation;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;


import java.util.Calendar;
import java.util.Scanner;
import java.net.URL;
import java.net.HttpURLConnection;
import org.json.simple.*;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import hudson.model.Result;
import java.net.MalformedURLException;


import java.net.MalformedURLException;

public class EvolvenBuilder extends Builder implements SimpleBuildStep {

    private final String apiUrl;
    private final String username;
    private final String password;
    private final String app;
    private final String envId;
    private final String hosts;

    @DataBoundConstructor
    public EvolvenBuilder(String apiUrl, String username, String password, String app, String envId, String hosts) {
        this.apiUrl = apiUrl;
        this.username = username;
        this.password = password;
        this.app = app;
        this.envId = envId;
        this.hosts = hosts;
    }

    public String getApiUrl() {
        return apiUrl;
    }
    public String getUsername() {
        return username;
    }
    public String getPassword() {
        return password;
    }
    public String getApp() {
        return app;
    }
    public String getEnvId() {
        return envId;
    }
    public String getHosts() {
        return hosts;
    }


    private String getDate(long millis){
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);

        int mYear = calendar.get(Calendar.YEAR);
        int mMonth = calendar.get(Calendar.MONTH);
        int mDay = calendar.get(Calendar.DAY_OF_MONTH);
        return String.format("%s/%s/%s", mMonth, mDay, mYear);
    }
    private JSONObject sendRequest(URL url, int step, TaskListener listener) {
        try {
            HttpURLConnection connection = null;
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            if (status == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line + "\n");
                }
                br.close();
                String response = sb.toString();
                Object obj = JSONValue.parse(response);
                JSONObject jsonObject = (JSONObject) obj;
                return jsonObject;
            }
        }
        catch (java.io.IOException io){
            listener.getLogger().println(io);
        }
        listener.getLogger().println("ERROR in step " + step);
        return new JSONObject();
    }
    private JSONArray sendRequest(URL url, TaskListener listener) {
        try {
            HttpURLConnection connection = null;
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            if (status == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line + "\n");
                }
                br.close();
                String response = sb.toString();
                JSONArray jsonArray = (JSONArray) JSONValue.parse(response);
                return jsonArray;
            }
        }
        catch (java.io.IOException io){
            listener.getLogger().println(io);
        }
        listener.getLogger().println("ERROR in step parsing JSONArray");
        return new JSONArray();
    }
    private String getCommits(TaskListener listener){
        try{
            URL githubURL = new URL("https://api.github.com/repos/evolven-software/evolven-api-python/branches");
            JSONArray branches = sendRequest(githubURL, listener);
            listener.getLogger().println(branches);
            String sha = "";
            for (int i = 0; i < branches.size(); i++) {
                JSONObject branchObj = (JSONObject)branches.get(i);
                if ((String)branchObj.get("name") == "master" ||
                        (String)branchObj.get("name") == "main")
                {
                    JSONObject branch = (JSONObject) branches.get(i);
                    JSONObject commitObj = (JSONObject) branch.get("commit");
                    sha = (String) commitObj.get("sha");
                    break;
                }
            }
            if (sha == ""){
                listener.getLogger().println("No Commits");
                System.exit(-1);
            }
            URL commitsURL = new URL(String.format("https://api.github.com/repos/evolven-software/" +
                    "evolven-api-python/commits?per_page=100&sha=%s", sha));
            JSONArray commits = sendRequest(commitsURL, listener);
            String commitIDs = "";
            for (int j=0; j<commits.size(); j++){
                JSONObject commit = (JSONObject) commits.get(j);
                String commitSha = (String) commit.get("sha");
                commitIDs += commit + ",";
            }
            commitIDs = commitIDs.substring(0, commitIDs.length() - 1);
            return commitIDs;
        }
        catch (MalformedURLException e){
            return "";
        }

    }
    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        //Use login to fetch session ID
        String deploymentId = run.getId();
        //Get deploymentKey
        String deploymentKey = run.getFullDisplayName();
        listener.getLogger().println(deploymentKey + " " + deploymentId);
        deploymentKey = deploymentKey.replaceAll("\\s", "");
        deploymentKey = deploymentKey.replaceAll("#", "");
        // Get deploymentStart
        long millis = run.getStartTimeInMillis();
        String deploymentStart = getDate(millis);
        // Get deployment commit ids
        String deploymentCommits = getCommits(listener);
        // Get deployment deeplink (Jenkins URL with deployment id)
        String deploymentDeeplink = run.getAbsoluteUrl();
        


        String loginUrl = String.format("/enlight.server/next/api?action=login&json=true&&" +
                "user=%s&pass=%s", this.username, this.password);
        URL loginRequest = new URL(this.apiUrl + loginUrl);
        JSONObject login = sendRequest(loginRequest, 1, listener);
        if (login.toString().equals("{}")) {
            run.setResult(Result.fromString("FAILURE"));
            System.exit(-1);
        }
        JSONObject next = (JSONObject) login.get("Next");
        String sessionId = (String) next.get("ID");
        listener.getLogger().println(login.toString());


        String blendedUrl = String.format("/enlight.server/next/blended?action=create&json=true&EvolvenSessionKey=%s" +
                "&event=$deploymentKey&eventId=%s&type=Deployment&source=Jenkins&host=%s&message=%s&start=%s" +
                "&envId=%s&ApplicationName=%s&deeplink=%s&authorized=true&automatic=true", sessionId, deploymentKey,
                deploymentId, hosts, deploymentCommits, deploymentStart, envId, app, deploymentDeeplink );
        URL blendedRequest = new URL(this.apiUrl + blendedUrl);
        JSONObject blended = sendRequest(blendedRequest, 2, listener);
        if (blended.toString().equals("{}")) {
            run.setResult(Result.fromString("FAILURE"));
            System.exit(-1);
        }
        listener.getLogger().println(blended.toString());


        String snapshotUrl = String.format("/enlight.server/next/bookmarks?action=create&json=true&EvolvenSessionKey=%s"
                + "&time=0&Name=%s&envId=%s&scan=true&start=true&urgent=true&fastMode=true", sessionId, deploymentKey,
                envId);
        URL snapshotRequest = new URL(this.apiUrl + snapshotUrl);
        JSONObject snapshot = sendRequest(snapshotRequest, 3, listener);
        if (snapshot.toString().equals("{}")) {
            run.setResult(Result.fromString("FAILURE"));
            System.exit(-1);
        }
        listener.getLogger().println(snapshot.toString());

        listener.getLogger().println("Evolven scan triggered.");

    }

    @Symbol("greet")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public FormValidation doCheckApiUrl(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("You have to put in an Evolven host url!");
            try{
                URL testURL = new URL(value);
            }
            catch (MalformedURLException e){
                return FormValidation.error("That's not a URL");
            }
            return FormValidation.ok();
        }
        public FormValidation doCheckUsername(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("You have to put in a username");
            return FormValidation.ok();
        }
        public FormValidation doCheckPassword(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("You have to put in a password");
            return FormValidation.ok();
        }
        public FormValidation doCheckApp(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("You have to put in an App");
            return FormValidation.ok();
        }
        public FormValidation doCheckEnvId(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("You have to put in an Environment ID");
            return FormValidation.ok();
        }
        public FormValidation doCheckHosts(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("You have to put in the hosts list");
            return FormValidation.ok();
        }
        // implement checks for variables

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }
        @Override
        public String getDisplayName() {
            return "Set Evolven Credentials";
        }

    }

}
