package com.zebrunner.jenkins.pipeline

import com.zebrunner.jenkins.BaseObject
import com.zebrunner.jenkins.jobdsl.factory.pipeline.BuildJobFactory
import com.zebrunner.jenkins.jobdsl.factory.pipeline.PublishJobFactory
import com.zebrunner.jenkins.jobdsl.factory.pipeline.DeployJobFactory
import com.zebrunner.jenkins.jobdsl.factory.pipeline.hook.PullRequestJobFactory
import com.zebrunner.jenkins.jobdsl.factory.pipeline.hook.PushJobFactory
import com.zebrunner.jenkins.jobdsl.factory.pipeline.scm.MergeJobFactory
import com.zebrunner.jenkins.jobdsl.factory.view.ListViewFactory
import com.zebrunner.jenkins.jobdsl.factory.folder.FolderFactory
import com.zebrunner.jenkins.pipeline.tools.scm.github.GitHub
import com.zebrunner.jenkins.pipeline.tools.scm.gitlab.Gitlab
import com.zebrunner.jenkins.pipeline.tools.scm.bitbucket.BitBucket
import com.zebrunner.jenkins.pipeline.runner.maven.TestNG
import com.zebrunner.jenkins.pipeline.runner.maven.Runner
import java.nio.file.Paths

import static com.zebrunner.jenkins.Utils.*
import static com.zebrunner.jenkins.pipeline.Executor.*

class Repository extends BaseObject {

    protected def library = ""
    protected def runnerClass
    protected def rootFolder
    protected def scmWebHookArgs
    protected def scmHost
    protected def scmOrg
    protected def repo
    protected def branch
    protected def scmUser
    protected def scmToken

    private static final String SCM_ORG = "scmOrg"
    private static final String SCM_HOST = "scmHost"
    private static final String REPO = "repo"
    private static final String BRANCH = "branch"
    private static final String SCM_USER = "scmUser"
    private static final String SCM_TOKEN = "scmToken"

    public Repository(context) {
        super(context)
        this.library = Configuration.get("pipelineLibrary")
        this.runnerClass = Configuration.get("runnerClass")
    }

    public void register() {
        logger.info("Repository->register")
        Configuration.set("GITHUB_ORGANIZATION", Configuration.get(SCM_ORG))
        Configuration.set("GITHUB_HOST", Configuration.get(SCM_HOST))

        this.scmHost = Configuration.get(SCM_HOST)
        this.scmOrg = Configuration.get(SCM_ORG)
        this.repo = Configuration.get(REPO)
        this.branch = Configuration.get(BRANCH)
        this.scmUser = Configuration.get(SCM_USER)
        this.scmToken = Configuration.get(SCM_TOKEN)

        logger.debug("scmHost: $scmHost scmOrg: $scmOrg repo: $repo branch: $branch")

        switch (scmHost) {
            case ~/^.*github.*$/:
                this.scmClient = new GitHub(context, scmHost, scmOrg, repo, branch)
                this.scmWebHookArgs = GitHub.getHookArgsAsMap(GitHub.HookArgs)
                break
            case ~/^.*gitlab.*$/:
                this.scmClient = new Gitlab(context, scmHost, scmOrg, repo, branch)
                this.scmWebHookArgs = Gitlab.getHookArgsAsMap(Gitlab.HookArgs)
                break
            case ~/^.*bitbucket.*$/:
                this.scmClient = new BitBucket(context, scmHost, scmOrg, repo, branch)
                this.scmWebHookArgs = BitBucket.getHookArgsAsMap(BitBucket.HookArgs)
                break
        }
        
        logger.debug("library: " + this.library)
        context.node('master') {
            context.timestamps {
                prepare()
                generateCiItems()
                clean()
            }
        }

        // execute new _trigger-<repo> to regenerate other views/jobs/etc
        def onPushJobLocation = Configuration.get(REPO) + "/onPush-" + Configuration.get(REPO)

        if (!isParamEmpty(this.rootFolder)) {
            onPushJobLocation = this.rootFolder + "/" + onPushJobLocation
        }

        context.build job: onPushJobLocation,
            propagate: true,
            parameters: [
                    context.string(name: 'repo', value: Configuration.get(REPO)),
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
        updateJenkinsCredentials("${this.scmOrg}-${this.repo}", "${githubOrganization} SCM token", this.scmUser, this.scmToken)
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

            // Folder from which RegisterRepository job was started
            // Important! using getOrgFolderNam from Utils is prohibited here!
            this.rootFolder = Paths.get(Configuration.get(Configuration.Parameter.JOB_NAME)).getName(0).toString()
            if ("RegisterRepository".equals(this.rootFolder)) {
                // use case when RegisterRepository is on root!
                this.rootFolder = "/"
                if (!isParamEmpty(reportingServiceUrl) && !isParamEmpty(reportingRefreshToken)) {
                    Organization.registerReportingCredentials("", reportingServiceUrl, reportingRefreshToken)
                }
            } else {
                if (!isParamEmpty(reportingServiceUrl) && !isParamEmpty(reportingRefreshToken)) {
                    Organization.registerReportingCredentials(repoFolder, reportingServiceUrl, reportingRefreshToken)
                }
            }
            
            logger.debug("organization: ${this.scmOrg}")
            logger.debug("rootFolder: " + this.rootFolder)

            if (!"/".equals(this.rootFolder)) {
                //For both cases when rootFolder exists job was started with existing organization value,
                //so it should be used by default
                Configuration.set(Configuration.Parameter.GITHUB_ORGANIZATION, this.scmOrg)
                repoFolder = this.rootFolder + "/" + repoFolder
            }

            logger.debug("repoFolder: " + repoFolder)

            // Support DEV related CI workflow
            // TODO: analyze do we need system jobs for QA repo... maybe prametrize CreateRepository call
            def gitUrl = Configuration.resolveVars("${Configuration.get(Configuration.Parameter.GITHUB_HTML_URL)}/${this.repo}")
            def userId = isParamEmpty(Configuration.get("userId")) ? '' : Configuration.get("userId")
            
            if (!isParamEmpty(this.library)) {
                //load custom library to check inheritance for isTestNGRunner
                logger.debug("load custom library to check inheritance for isTestNGRunner: " + this.library)
                context.library this.library
            }

            def isTestNgRunner = extendsClass([TestNG])

            // TODO: move folder and main trigger job creation onto the createRepository method
            registerObject("project_folder", new FolderFactory(repoFolder, ""))
            registerObject("hooks_view", new ListViewFactory(repoFolder, 'SYSTEM', null, ".*onPush.*|.*onPullRequest.*|.*CutBranch-.*|build|deploy|publish"))
            registerObject("merge_job", new MergeJobFactory(repoFolder, getMergeScript(), "CutBranch-${this.repo}", this.scmHost, this.scmOrg, this.repo, gitUrl))
            registerObject("push_job", new PushJobFactory(repoFolder, getOnPushScript(), "onPush-${this.repo}", this.scmHost, this.scmOrg, this.repo, this.branch, gitUrl, userId, isTestNgRunner, zafiraFields))
            registerObject("pull_request_job", new PullRequestJobFactory(repoFolder, getOnPullRequestScript(), "onPullRequest-${this.repo}", this.scmHost, this.scmOrg, this.repo, gitUrl))

            def isBuildToolDependent = extendsClass([com.zebrunner.jenkins.pipeline.runner.maven.Runner, com.zebrunner.jenkins.pipeline.runner.gradle.Runner, com.zebrunner.jenkins.pipeline.runner.docker.Runner])

            if (isBuildToolDependent) {
                def buildTool = determineBuildTool()
                def isDockerRunner = false

                if (extendsClass([com.zebrunner.jenkins.pipeline.runner.docker.Runner])) {
                    if (isParamEmpty(getCredentials(githubOrganization + '-docker'))) {
                        updateJenkinsCredentials(githubOrganization + '-docker', 'docker hub creds', Configuration.Parameter.DOCKER_HUB_USERNAME.getValue(), Configuration.Parameter.DOCKER_HUB_PASSWORD.getValue())
                    }

                    isDockerRunner = true
                    registerObject("deploy_job", new DeployJobFactory(repoFolder, getDeployScript(), "deploy", this.scmHost, this.scmOrg, this.repo))
                    registerObject("publish_job", new PublishJobFactory(repoFolder, getPublishScript(), "publish", this.scmHost, this.scmOrg, this.repo, this.branch))
                }

                registerObject("build_job", new BuildJobFactory(repoFolder, getPipelineScript(), "build", this.scmHost, this.scmOrg, this.repo, this.branch, buildTool, isDockerRunner))
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

    protected String getPipelineScript() {
        return "${getPipelineLibrary(this.library)}\nimport ${runnerClass};\nnew ${runnerClass}(this).build()"
    }

    protected String getMergeScript() {
        return "${getPipelineLibrary(this.library)}\nimport ${runnerClass};\nnew ${runnerClass}(this).mergeBranch()"
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

    protected String determineBuildTool() {
        def buildTool = "undefined"

        def mavenRepo = context.fileExists 'pom.xml'
        def gradleRepo = context.fileExists 'build.gradle'

        if (!(mavenRepo && gradleRepo)) {
            if (mavenRepo) buildTool = "maven"
            else if (gradleRepo) buildTool = "gradle"
        }

        logger.debug("buildTool: " + buildTool)

        return buildTool
    }

    public def registerCredentials() {
        context.stage("Register Credentials") {
            def jenkinsUser = !isParamEmpty(Configuration.get("jenkinsUser")) ? Configuration.get("jenkinsUser") : getBuildUser(context.currentBuild)
            if (updateJenkinsCredentials("token_" + jenkinsUser, jenkinsUser + " SCM token", this.scmUser, this.scmToken)) {
                logger.info(jenkinsUser + " credentials were successfully registered.")
            } else {
                throw new RuntimeException("Required fields are missing.")
            }
        }
    }

}