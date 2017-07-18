package com.arkea.satd.stoplightio;
import java.io.File;
import java.io.IOException;
import java.util.Collections;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.arkea.satd.stoplightio.model.Collection;
import com.arkea.satd.stoplightio.parsers.ConsoleParser;
import com.arkea.satd.stoplightio.parsers.JsonResultParser;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;

/**
 * Publisher Plugin for Stoplight / Scenario / Prism 
 *
 * When a build is performed, the {@link #perform} method will be invoked. 
 *
 * @author Nicolas TISSERAND
 */
public class StoplightReportPublisher extends Recorder {

	////config.jelly fields
	//Radio Button Selection
	private final String consoleOrFile;
	private final String resultFile;
	
	private static final String CONSOLE = "console";

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public StoplightReportPublisher(String consoleOrFile, String resultFile) {
        this.consoleOrFile = consoleOrFile;
        this.resultFile = resultFile;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

   
    @Override public java.util.Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        return Collections.singleton(new StoplightReportProjectAction(project));
    }
        
    /**
     * Used in {@code config.jelly}.
     * Returns the path of the file to parse
     * @return the path of the file to parse
     */
    public String getResultFile() {
        return resultFile;
    }

    /**
     * Used in {@code config.jelly}.
     * Manage the checking of radioBlock.
     * By default, console is checked  
     * @param testTypeName accepted values : "console" or "file"
     * @return true if testTypeName is null or empty or equals to "console" 
     */
    public String isTestType(String testTypeName) {
    	
    	if(consoleOrFile==null) {
    		return CONSOLE.equals(testTypeName) ? "true" : "";
    	} else {
    		return consoleOrFile.equalsIgnoreCase(testTypeName) ? "true" : "";
    	}
    }    
    
    
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {

    	File f;
    	if(consoleOrFile==null || consoleOrFile.isEmpty() || CONSOLE.equals(consoleOrFile)) {
    		f = build.getLogFile();
    	} else {
        	String wsBasePath = "";
        	try {
        		wsBasePath = build.getEnvironment(listener).get("WORKSPACE");
			} catch (IOException | InterruptedException e) {
				listener.getLogger().println("The environment variable WORKSPACE doesn't exists");
				e.printStackTrace();
			}
        	
        	String prepareFileLocation = resultFile.replace("${WORKSPACE}", wsBasePath).replace("%WORKSPACE%", wsBasePath);
        	f = new File(prepareFileLocation);    		
    	}

    	if(f.exists()) {
	   		listener.getLogger().println("Parsing " + f.getAbsolutePath());
	   		
	   		Collection coll;
	   		try {
	   			coll = JsonResultParser.parse(f);
	   		} catch(Exception e) {
	   			coll = ConsoleParser.parse(f);	   			
	   		}
	
			StoplightReportBuildAction buildAction = new StoplightReportBuildAction(build, coll);
			build.addAction(buildAction);
			
			return true;
    	} else {
    		listener.getLogger().println("The file " + resultFile + " doesn't exists");
    		return false;
    	}
    	
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link StoplightReportPublisher}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See {@code src/main/resources/com/arkea/satd/stoplightio/StoplightReportPublisher/*.jelly}
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension @Symbol("stoplightio-report") // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <p>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user. 
         */
        public FormValidation doCheckResultFile(@QueryParameter String value) {
            if (value.length() == 0)
                return FormValidation.error("Please set a path");
            if (value.length() < 4)
                return FormValidation.warning("Isn't the path too short?");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Publish Stoplight Report";
        }


    }
}
