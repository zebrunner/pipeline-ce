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
import java.nio.file.Paths

import static com.zebrunner.jenkins.Utils.*
import static com.zebrunner.jenkins.pipeline.Executor.*

class Repository extends BaseObject {

    protected def library = ""
    protected def runnerClass
    
    protected def branch

    private static final String BRANCH = "branch"

    public Repository(context) {
        super(context)
        this.library = Configuration.get("pipelineLibrary")
        this.runnerClass = Configuration.get("runnerClass")
    }

    public void register() {
        logger.info("Repository->register")
        
        this.branch = Configuration.get(BRANCH)

        logger.debug("repoUrl: ${this.repoUrl}; repo: ${this.repo}; branch: ${this.branch}")

        logger.debug("library: " + this.library)
        context.node('master') {
            context.timestamps {
                prepare()
                generateCiItems()
                clean()
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
                    context.booleanParam(name: 'onlyUpdated', value: false),
                    context.string(name: 'removedConfigFilesAction', value: 'DELETE'),
                    context.string(name: 'removedJobAction', value: 'DELETE'),
                    context.string(name: 'removedViewAction', value: 'DELETE'),
            ]
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
        
        
        def scmTokenCreds = "${this.repo}"
        if (!isParamEmpty(this.organization)) {
            scmTokenCreds = "${this.organization}-${this.repo}"
        }
        updateJenkinsCredentials(scmTokenCreds, "${this.repo} SCM token", this.scmUser, this.scmToken)
        getScm().clone(true)
    }


    private void generateCiItems() {
        context.stage("Create Repository") {
            def buildNumber = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
            def repoFolder = this.repo

            //Job build display name
            context.currentBuild.displayName = "#${buildNumber}|${this.repo}|${this.branch}"

            //TODO: refactor removing zafira naming
            def zafiraFields = isParamEmpty(Configuration.get("zafiraFields")) ? '' : Configuration.get("zafiraFields")
            def reportingServiceUrl = ""
            def reportingRefreshToken = "'"
            logger.debug("zafiraFields: " + zafiraFields)
            if (!isParamEmpty(zafiraFields) && zafiraFields.contains("zafira_service_url") && zafiraFields.contains("zafira_access_token")) {
                reportingServiceUrl = Configuration.get("zafira_service_url")
                reportingRefreshToken = Configuration.get("zafira_access_token")
                logger.debug("reportingServiceUrl: " + reportingServiceUrl)
                logger.debug("reportingRefreshToken: " + reportingRefreshToken)
            }

            if (!isParamEmpty(reportingServiceUrl) && !isParamEmpty(reportingRefreshToken)) {
                Organization.registerReportingCredentials(this.organization, reportingServiceUrl, reportingRefreshToken)
            }
            
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
            
            def prJobDesc = "Follow Configuration Guide steps to setup WebHook: https://zebrunner.github.io/zebrunner/config-guide/#setup-webhook-events-push-and-pull-requests"
            def pushJobDesc = "Follow Configuration Guide steps to setup WebHook: https://zebrunner.github.io/zebrunner/config-guide/#setup-webhook-events-push-and-pull-requests"


            // TODO: move folder and main trigger job creation onto the createRepository method
            registerObject("project_folder", new FolderFactory(repoFolder, ""))
            registerObject("hooks_view", new ListViewFactory(repoFolder, 'SYSTEM', null, ".*onPush.*|.*onPullRequest.*|.*CutBranch-.*|build|deploy|publish"))
            registerObject("push_job", new PushJobFactory(repoFolder, getOnPushScript(), "onPush-${this.repo}", pushJobDesc, this.organization, this.repoUrl, this.branch, userId, isTestNgRunner, zafiraFields, scmClient.webHookArgs()))
            registerObject("pull_request_job", new PullRequestJobFactory(repoFolder, getOnPullRequestScript(), "onPullRequest-${this.repo}", prJobDesc, this.organization, this.repoUrl, this.branch, scmClient.webHookArgs()))

            def isBuildToolDependent = extendsClass([com.zebrunner.jenkins.pipeline.runner.maven.Runner, com.zebrunner.jenkins.pipeline.runner.gradle.Runner, com.zebrunner.jenkins.pipeline.runner.docker.Runner])
            if (isBuildToolDependent) {
                def isDockerRunner = false

                if (extendsClass([com.zebrunner.jenkins.pipeline.runner.docker.Runner])) {
                    if (isParamEmpty(getCredentials("${this.organization}-docker"))) {
                        updateJenkinsCredentials("${this.organization}-docker", 'docker hub creds', Configuration.Parameter.DOCKER_HUB_USERNAME.getValue(), Configuration.Parameter.DOCKER_HUB_PASSWORD.getValue())
                    }

                    isDockerRunner = true
                    registerObject("deploy_job", new DeployJobFactory(repoFolder, getDeployScript(), "deploy", this.repoUrl))
                    registerObject("publish_job", new PublishJobFactory(repoFolder, getPublishScript(), "publish", this.repoUrl, this.branch))
                }

                registerObject("build_job", new BuildJobFactory(repoFolder, getBuildScript(), "build", this.repoUrl, this.branch, isDockerRunner))
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