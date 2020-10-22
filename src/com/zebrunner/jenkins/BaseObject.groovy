package com.zebrunner.jenkins

import com.zebrunner.jenkins.Logger
import com.zebrunner.jenkins.pipeline.Configuration
import com.zebrunner.jenkins.pipeline.tools.scm.ISCM

import com.zebrunner.jenkins.pipeline.tools.scm.github.GitHub
import com.zebrunner.jenkins.pipeline.tools.scm.gitlab.Gitlab
import com.zebrunner.jenkins.pipeline.tools.scm.bitbucket.BitBucket

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
    protected def scmWebHookArgs

    protected def currentBuild
    protected String displayNameTemplate = '#${BUILD_NUMBER}|${branch}'
    protected final String DISPLAY_NAME_SEPARATOR = "|"
    
    protected String zebrunnerPipeline // pipeline name and version!

    //this is very important line which should be declared only as a class member!
    protected Configuration configuration = new Configuration(context)

    public BaseObject(context) {
        this.context = context
        this.logger = new Logger(context)
        this.dslObjects = new LinkedHashMap()

        this.factoryRunner = new FactoryRunner(context)

        this.zebrunnerPipeline = "Zebrunner-CE@" + Configuration.get(Configuration.Parameter.ZEBRUNNER_VERSION)
        currentBuild = context.currentBuild
        
        def String gitType = Configuration.get(Configuration.Parameter.GIT_TYPE)
        switch (gitType) {
            case "github":
                this.scmClient = new GitHub(context)
                this.scmWebHookArgs = GitHub.getHookArgsAsMap(GitHub.HookArgs)
                break
            case "gitlab":
                this.scmClient = new Gitlab(context)
                this.scmWebHookArgs = Gitlab.getHookArgsAsMap(Gitlab.HookArgs)
                break
            case "bitbucket":
                this.scmClient = new BitBucket(context)
                this.scmWebHookArgs = BitBucket.getHookArgsAsMap(BitBucket.HookArgs)
                break
            default:
                throw new RuntimeException("Unsuported source control management: ${gitType}!")
        }
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

    public def getScm() {
        return this.scmClient
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
        if ("Zebrunner-CE".equals(customPipeline) || customPipeline.isEmpty()) {
            // no custom private pipeline detected!
            return "@Library(\'${zebrunnerPipeline}\')"
        } else {
            return "@Library(\'${zebrunnerPipeline}\')\n@Library(\'${customPipeline}\')"
        }
    }
}
