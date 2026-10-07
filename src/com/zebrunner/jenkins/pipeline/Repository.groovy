package com.zebrunner.jenkins.pipeline

import com.zebrunner.jenkins.BaseObject
import com.zebrunner.jenkins.jobdsl.factory.pipeline.BuildJobFactory
import com.zebrunner.jenkins.jobdsl.factory.pipeline.PublishJobFactory
import com.zebrunner.jenkins.jobdsl.factory.pipeline.DeployJobFactory
import com.zebrunner.jenkins.jobdsl.factory.pipeline.hook.PullRequestJobFactory
import com.zebrunner.jenkins.jobdsl.factory.pipeline.hook.PushJobFactory
import com.zebrunner.jenkins.jobdsl.factory.view.ListViewFactory
import com.zebrunner.jenkins.jobdsl.factory.folder.FolderFactory
import com.zebrunner.jenkins.pipeline.runner.maven.TestNG
import com.zebrunner.jenkins.pipeline.runner.maven.Runner
import hudson.BulkChange
import hudson.model.ParametersAction
import hudson.model.ParametersDefinitionProperty
import hudson.model.StringParameterDefinition
import hudson.security.ACL
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.CredentialsParameterDefinition
import com.cloudbees.plugins.credentials.common.StandardCredentials
import java.nio.file.Paths

import static com.zebrunner.jenkins.Utils.*
import static com.zebrunner.jenkins.pipeline.Executor.*

class Repository extends BaseObject {

    protected def library = ""
    protected def runnerClass
    
    protected def branch

    private static final String BRANCH = "branch"
    private static final String PAT_CREDENTIALS_ID = "scmPatCredentialsId"
    private static final String GITHUB_APP_CREDENTIALS_ID = "scmGitHubAppCredentialsId"
    private static final String PAT_CREDENTIALS_TYPE = "com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl"
    private static final String GITHUB_APP_CREDENTIALS_TYPE = "org.jenkinsci.plugins.github_branch_source.GitHubAppCredentials"
    private static final String SCM_CREDENTIALS_DESCRIPTION = 'Optional credential for repository checkout: a GitHub App or username/password credential containing a PAT.'

    public Repository(context) {
        super(context)
        this.library = Configuration.get("pipelineLibrary")
        this.runnerClass = Configuration.get("runnerClass")
    }

    public void register() {
        logger.info("Repository->register")
        if (updateRegistrationParameters()) {
            logger.info("RegisterRepository parameters updated. Reopen Build with Parameters and run the job again.")
            return
        }
        
        this.branch = Configuration.get(BRANCH)
        this.scmCredentialsId = selectScmCredentialsId()
        this.scmClient.setCredentialsId(this.scmCredentialsId)

        logger.debug("repoUrl: ${this.repoUrl}; repo: ${this.repo}; branch: ${this.branch}")

        logger.debug("library: " + this.library)
        context.node(getMavenNodeLabel()) {
            context.timestamps {
                context.withEnv(getVariables(Configuration.VARIABLES_ENV)) { // read values from variables.env
                    prepare()
                    generateCiItems()
                    clean()
                }
            }
        }

        // execute new _trigger-<repo> to regenerate other views/jobs/etc
        def onPushJobLocation = this.repo + "/onPush-" + this.repo

        if (!isParamEmpty(this.organization)) {
            onPushJobLocation = this.organization + "/" + onPushJobLocation
        }

        context.build job: onPushJobLocation,
            propagate: true,
            parameters: [
                    context.string(name: 'repoUrl', value: this.repoUrl),
                    context.string(name: 'branch', value: Configuration.get(BRANCH)),
                    context.string(name: 'node_label', value: Configuration.get('node_label')),
                    context.string(name: SCM_CREDENTIALS_ID, value: this.scmCredentialsId),
                    context.booleanParam(name: 'onlyUpdated', value: false),
                    context.string(name: 'removedConfigFilesAction', value: 'DELETE'),
                    context.string(name: 'removedJobAction', value: 'DELETE'),
                    context.string(name: 'removedViewAction', value: 'DELETE'),
            ]
    }

    @NonCPS
    private boolean updateRegistrationParameters() {
        def job = context.currentBuild.rawBuild.parent
        synchronized (job) {
            def property = job.getProperty(ParametersDefinitionProperty)
            def definitions = new ArrayList(property?.parameterDefinitions ?: [])
            boolean removedLegacyParameters = definitions.removeAll {
                it.name in ['scmUser', 'scmToken', PAT_CREDENTIALS_ID, GITHUB_APP_CREDENTIALS_ID]
            }
            boolean addNodeLabel = !definitions.any { it.name == 'node_label' }
            def credentialDefinitions = definitions.findAll { it.name == SCM_CREDENTIALS_ID }
            def credentialDefinition = credentialDefinitions ? credentialDefinitions[0] : null
            boolean updateCredentialSelector = credentialDefinitions.size() != 1 ||
                !(credentialDefinition instanceof CredentialsParameterDefinition) ||
                credentialDefinition.credentialType != StandardCredentials.class.name ||
                credentialDefinition.defaultValue != '' ||
                credentialDefinition.required ||
                credentialDefinition.description != SCM_CREDENTIALS_DESCRIPTION
            if (!removedLegacyParameters && !addNodeLabel && !updateCredentialSelector) {
                return false
            }

            if (addNodeLabel) {
                definitions.add(new StringParameterDefinition('node_label', '', 'Optional agent label. Leave empty to use the configured node.'))
            }
            if (updateCredentialSelector) {
                int index = credentialDefinition != null ? definitions.indexOf(credentialDefinition) : definitions.size()
                definitions.removeAll { it.name == SCM_CREDENTIALS_ID }
                definitions.add(index, new CredentialsParameterDefinition(SCM_CREDENTIALS_ID,
                    SCM_CREDENTIALS_DESCRIPTION, '', StandardCredentials.class.name, false))
            }
            def change = new BulkChange(job)
            try {
                if (property != null) {
                    job.removeProperty(property)
                }
                job.addProperty(new ParametersDefinitionProperty(definitions))
                change.commit()
            } finally {
                change.abort()
            }
            return true
        }
    }

    private String selectScmCredentialsId() {
        def buildParameters = context.currentBuild.rawBuild.getAction(ParametersAction)
        String selectedId = buildParameters?.getParameter(SCM_CREDENTIALS_ID)?.value?.toString()?.trim() ?: ''
        if (!selectedId) {
            return ''
        }
        def credential = CredentialsProvider.lookupCredentials(StandardCredentials.class,
            context.currentBuild.rawBuild.parent, ACL.SYSTEM).find { it.id == selectedId }
        String selectedType = credential?.getClass()?.getName()
        if (selectedType != PAT_CREDENTIALS_TYPE && selectedType != GITHUB_APP_CREDENTIALS_TYPE) {
            throw new IllegalArgumentException("Credential '${selectedId}' is unavailable in the global or organization folder store, or is not a PAT or GitHub App credential. Personal-only credentials are unsupported.")
        }
        if (selectedType == GITHUB_APP_CREDENTIALS_TYPE && !'github'.equalsIgnoreCase(Configuration.get('scmType'))) {
            throw new IllegalArgumentException('GitHub App credentials require scmType github.')
        }
        if (!this.repoUrl.startsWith('https://')) {
            throw new IllegalArgumentException('PAT and GitHub App credentials require an HTTPS repository URL.')
        }
        return selectedId
    }

    public void create() {
        //TODO: incorporate maven project generation based on archetype (carina?)
        throw new RuntimeException("Not implemented yet!")

    }

    protected void prepare() {
        def webhookTokenCreds = "${Configuration.get("scmType")}-webhook-token"
        if (!isParamEmpty(this.organization)) {
            webhookTokenCreds = "${this.organization}-${Configuration.get("scmType")}-webhook-token"
        }
        
        if (!getCredentials(webhookTokenCreds)) {
            updateJenkinsCredentials(webhookTokenCreds, webhookTokenCreds, "CHANGE_ME")
        }
        
        
        getScm().clone(true)
    }


    private void generateCiItems() {
        context.stage("Create Repository") {
            def buildNumber = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
            def repoFolder = this.repo

            //Job build display name
            context.currentBuild.displayName = "#${buildNumber}|${this.repo}|${this.branch}"

            def reportingServiceUrl = ""
            def reportingRefreshToken = "'"

            logger.debug("organization: ${this.organization}")

            repoFolder = this.organization + "/" + repoFolder
            logger.debug("repoFolder: " + repoFolder)

            // Support DEV related CI workflow
            // TODO: analyze do we need system jobs for QA repo... maybe prametrize CreateRepository call
            def userId = isParamEmpty(Configuration.get("userId")) ? '' : Configuration.get("userId")
            
            if (!isParamEmpty(this.library)) {
                //load custom library to check inheritance for isTestNGRunner
                logger.debug("load custom library to check inheritance for isTestNGRunner: " + this.library)
                context.library this.library
            }

            def isTestNgRunner = extendsClass([TestNG])
            
            def systemJobDesc = "Configuration Guide: https://zebrunner.github.io/community-edition/config-guide"


            // TODO: move folder and main trigger job creation onto the createRepository method
            registerObject("project_folder", new FolderFactory(repoFolder, ""))
            registerObject("hooks_view", new ListViewFactory(repoFolder, 'SYSTEM', null, ".*onPush.*|.*onPullRequest.*|.*CutBranch-.*|build|deploy|publish"))
            registerObject("push_job", new PushJobFactory(repoFolder, getOnPushScript(), "onPush-${this.repo}", systemJobDesc, this.organization, this.repoUrl, this.branch, userId, isTestNgRunner, scmClient.webHookArgs(), Configuration.get('node_label'), this.scmCredentialsId))
            registerObject("pull_request_job", new PullRequestJobFactory(repoFolder, getOnPullRequestScript(), "onPullRequest-${this.repo}", systemJobDesc, this.organization, this.repoUrl, this.branch, scmClient.webHookArgs(), Configuration.get('node_label'), this.scmCredentialsId))

            def isBuildToolDependent = extendsClass([com.zebrunner.jenkins.pipeline.runner.maven.Runner, com.zebrunner.jenkins.pipeline.runner.gradle.Runner, com.zebrunner.jenkins.pipeline.runner.docker.Runner])
            if (isBuildToolDependent) {
                def isDockerRunner = false

                if (extendsClass([com.zebrunner.jenkins.pipeline.runner.docker.Runner])) {
                    if (isParamEmpty(getCredentials("${this.organization}-docker"))) {
                        updateJenkinsCredentials("${this.organization}-docker", 'docker hub creds', Configuration.Parameter.DOCKER_HUB_USERNAME.getValue(), Configuration.Parameter.DOCKER_HUB_PASSWORD.getValue())
                    }

                    isDockerRunner = true
                    registerObject("deploy_job", new DeployJobFactory(repoFolder, getDeployScript(), "deploy", this.repoUrl, this.scmCredentialsId))
                    registerObject("publish_job", new PublishJobFactory(repoFolder, getPublishScript(), "publish", this.repoUrl, this.branch, this.scmCredentialsId))
                }

                registerObject("build_job", new BuildJobFactory(repoFolder, getBuildScript(), "build", systemJobDesc, this.repoUrl, this.branch, isDockerRunner, this.scmCredentialsId))
            }

            logger.debug("before - factoryRunner.run(dslObjects)")
            factoryRunner.run(dslObjects)
            logger.debug("after - factoryRunner.run(dslObjects)")

        }
    }

    private String getOnPullRequestScript() {
        return "${getPipelineLibrary(this.library)}\nimport ${runnerClass}\nnew ${runnerClass}(this).onPullRequest()"
    }

    private String getOnPushScript() {
        return "${getPipelineLibrary(this.library)}\nimport ${runnerClass}\nnew ${runnerClass}(this).onPush()"
    }

    protected String getBuildScript() {
        return "${getPipelineLibrary(this.library)}\nimport ${runnerClass};\nnew ${runnerClass}(this).build()"
    }

    protected String getPublishScript() {
        return "${getPipelineLibrary(this.library)}\nimport ${runnerClass};\nnew ${runnerClass}(this).publish()"
    }

    protected String getDeployScript() {
        return "${getPipelineLibrary(this.library)}\nimport ${runnerClass};\nnew ${runnerClass}(this).deploy()"
    }

    protected boolean extendsClass(classes) {
        return classes.any { Class.forName(this.runnerClass, false, Thread.currentThread().getContextClassLoader()) in it } 
    }

}
