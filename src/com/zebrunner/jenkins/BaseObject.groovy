package com.zebrunner.jenkins

import com.zebrunner.jenkins.Logger
import com.zebrunner.jenkins.pipeline.Configuration
import com.zebrunner.jenkins.pipeline.tools.scm.ISCM

import com.zebrunner.jenkins.pipeline.tools.scm.github.GitHub
import com.zebrunner.jenkins.pipeline.tools.scm.github.ssh.SshGitHub

import static com.zebrunner.jenkins.Utils.replaceMultipleSymbolsToOne

/*
 * BaseObject to operate with pipeline context, loggers and runners
 */

public abstract class BaseObject {
    protected def context
    protected Logger logger
    protected FactoryRunner factoryRunner // object to be able to start JobDSL anytime we need
    protected Map dslObjects

    protected ISCM scmClient
    protected ISCM scmSshClient

    protected boolean isSsh = false

    protected def currentBuild
    protected String displayNameTemplate = '#${BUILD_NUMBER}|${branch}'
    protected final String DISPLAY_NAME_SEPARATOR = "|"
    
    protected String zebrunnerPipeline // pipeline name and version!

    //this is very important line which should be declared only as a class member!
    protected Configuration configuration = new Configuration(context)

    public BaseObject(context) {
        this.context = context
        this.logger = new Logger(context)
        this.scmClient = new GitHub(context)
        this.scmSshClient = new SshGitHub(context)
        this.dslObjects = new LinkedHashMap()

        this.factoryRunner = new FactoryRunner(context)

        //TODO: test if getter works in constructor
        this.zebrunnerPipeline = "Zebrunner-CE@" + Configuration.get(Configuration.Parameter.ZEBRUNNER_VERSION)
        currentBuild = context.currentBuild
    }

    protected String getDisplayName() {
        def String displayName = Configuration.resolveVars(this.displayNameTemplate)
        displayName = displayName.replaceAll("(?i)null", '')
        displayName = replaceMultipleSymbolsToOne(displayName, DISPLAY_NAME_SEPARATOR)
        return displayName
    }

    @NonCPS
    protected void setDisplayNameTemplate(String template) {
        this.displayNameTemplate = template
    }

    @NonCPS
    public def setSshClient() {
        this.isSsh = true
    }

    public def getScm() {
        if (this.isSsh) {
            return this.scmSshClient
        } else {
            return this.scmClient
        }
    }
    
    protected void registerObject(name, object) {
        if (dslObjects.containsKey(name)) {
            logger.debug("key ${name} already defined and will be replaced!")
            logger.debug("Old Item: ${dslObjects.get(name).dump()}")
            logger.debug("New Item: ${object.dump()}")
        }
        dslObjects.put(name, object)
    }

    protected void clean() {
        context.stage('Wipe out Workspace') {
            context.deleteDir()
        }
    }
    
    protected String getPipelineLibrary() {
        return getPipelineLibrary("")
    }
    
    protected String getPipelineLibrary(customPipeline) {
        if (customPipeline.isEmpty()) {
            // no custom private pipeline detected!
            return "@Library(\'${zebrunnerPipeline}\')"
        } else {
            return "@Library(\'${zebrunnerPipeline}\')\n@Library(\'${customPipeline}\')"
        }
    }
}
