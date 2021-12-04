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
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import java.io.InputStreamReader;
import java.io.BufferedReader;


public class HelloWorldBuilder extends Builder implements SimpleBuildStep {

    private final String apiUrl;
    private final String username;
    private final String password;
    private final String app;
    private final String envId;
    private final String hosts;

    @DataBoundConstructor
    public HelloWorldBuilder(String apiUrl, String username, String password, String app, String envId, String hosts) {
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


    /*@DataBoundSetter
    public void setUseFrench(boolean useFrench) {
        this.useFrench = useFrench;
    }*/
    public String getDate(long millis){
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);

        int mYear = calendar.get(Calendar.YEAR);
        int mMonth = calendar.get(Calendar.MONTH);
        int mDay = calendar.get(Calendar.DAY_OF_MONTH);
        return String.format("%s/%s/%s", mMonth, mDay, mYear);
    }
    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        //Use login to fetch session ID
        String deployment_id = run.getId();
        //Get deployment_key
        String deployment_key = run.getFullDisplayName();
        deployment_key = deployment_key.replaceAll("\\s", "");
        // Get deployment_start
        long millis = run.getStartTimeInMillis();
        String deployment_start = getDate(millis);
        // Get deployment commit ids
        // Get deployment deeplink (Jenkins URL with deployment id)
        //String deployment_deeplink = run.getAbsoluteUrl();
        String deployment_deeplink = "http://localhost:8080/jenkins/a/5";

        String loginUrl = String.format("/enlight.server/next/api?action=login&json=true&&" +
                "user=%s&pass=%s", this.username, this.password);
        URL loginRequest = new URL(this.apiUrl + loginUrl);

        HttpURLConnection connection = null;
        connection = (HttpURLConnection) loginRequest.openConnection();
        connection.setRequestMethod("GET");
        int status = connection.getResponseCode();
        if (status == 200){
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line+"\n");
            }
            br.close();
            String response = sb.toString();
            Object obj=JSONValue.parse(response);
            JSONObject jsonObject = (JSONObject) obj;
            JSONObject next = (JSONObject) jsonObject.get("Next");
            String sessionId = (String) next.get("ID");
        }
        else {
            listener.getLogger().println("Login Error.");
        }



    }

    @Symbol("greet")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckName(@QueryParameter String value, @QueryParameter boolean useFrench)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error(Messages.HelloWorldBuilder_DescriptorImpl_errors_missingName());
            if (value.length() < 4)
                return FormValidation.warning(Messages.HelloWorldBuilder_DescriptorImpl_warnings_tooShort());
            if (!useFrench && value.matches(".*[éáàç].*")) {
                return FormValidation.warning(Messages.HelloWorldBuilder_DescriptorImpl_warnings_reallyFrench());
            }
            return FormValidation.ok();
        }
        // implement checks for variables

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.HelloWorldBuilder_DescriptorImpl_DisplayName();
        }

    }

}
