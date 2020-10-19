package com.zebrunner.jenkins.pipeline

import com.zebrunner.jenkins.BaseObject
import com.zebrunner.jenkins.pipeline.tools.scm.ISCM
import com.zebrunner.jenkins.pipeline.tools.scm.github.GitHub
import com.zebrunner.jenkins.jobdsl.factory.job.hook.PullRequestJobFactoryTrigger
import com.zebrunner.jenkins.jobdsl.factory.pipeline.hook.PushJobFactory
import com.zebrunner.jenkins.jobdsl.factory.pipeline.BuildJobFactory
import com.zebrunner.jenkins.jobdsl.factory.pipeline.PublishJobFactory
import com.zebrunner.jenkins.jobdsl.factory.pipeline.DeployJobFactory
import com.zebrunner.jenkins.jobdsl.factory.pipeline.hook.PullRequestJobFactory
import com.zebrunner.jenkins.jobdsl.factory.pipeline.scm.MergeJobFactory
import com.zebrunner.jenkins.jobdsl.factory.view.ListViewFactory
import com.zebrunner.jenkins.jobdsl.factory.folder.FolderFactory
import com.zebrunner.jenkins.pipeline.runner.maven.TestNG
import com.zebrunner.jenkins.pipeline.runner.maven.Runner
import java.nio.file.Paths
import static com.zebrunner.jenkins.Utils.*
import static com.zebrunner.jenkins.pipeline.Executor.*

class Repository extends BaseObject {

    protected ISCM scmClient
    protected def library = ""
    protected def runnerClass
    protected def rootFolder
    private static final String SCM_ORG = "scmOrg"
    private static final String SCM_HOST = "scmHost"
    private static final String REPO = "repo"
    private static final String BRANCH = "branch"
    private static final String SCM_USER = "scmUser"
    private static final String SCM_TOKEN = "scmToken"

    public Repository(context) {
        super(context)

        scmClient = new GitHub(context)
        this.library = Configuration.get("pipelineLibrary")
        this.runnerClass = Configuration.get("runnerClass")
    }

    public void register() {
        logger.info("Repository->register")
        Configuration.set("GITHUB_ORGANIZATION", Configuration.get(SCM_ORG))
        Configuration.set("GITHUB_HOST", Configuration.get(SCM_HOST))
        
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
        def githubOrganization = Configuration.get(SCM_ORG)
        def credentialsId = "${githubOrganization}-${Configuration.get(REPO)}"

        updateJenkinsCredentials(credentialsId, "${githubOrganization} SCM token", Configuration.get(SCM_USER), Configuration.get(SCM_TOKEN))

        getScm().clone(true)
    }


    private void generateCiItems() {
        context.stage("Create Repository") {
            def buildNumber = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
            def repoFolder = Configuration.get(REPO)

            
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
            
            logger.debug("organization: " + Configuration.get(SCM_ORG))
            logger.debug("rootFolder: " + this.rootFolder)

            if (!"/".equals(this.rootFolder)) {
                //For both cases when rootFolder exists job was started with existing organization value,
                //so it should be used by default
                Configuration.set(Configuration.Parameter.GITHUB_ORGANIZATION, Configuration.get(SCM_ORG))
                repoFolder = this.rootFolder + "/" + repoFolder
            }

            logger.debug("repoFolder: " + repoFolder)

            //Job build display name
            context.currentBuild.displayName = "#${buildNumber}|${Configuration.get(REPO)}|${Configuration.get(BRANCH)}"

            def githubHost = Configuration.get(SCM_HOST)
            def githubOrganization = Configuration.get(SCM_ORG)

            registerObject("project_folder", new FolderFactory(repoFolder, ""))
            // TODO: move folder and main trigger job creation onto the createRepository method

            // Support DEV related CI workflow
            // TODO: analyze do we need system jobs for QA repo... maybe prametrize CreateRepository call
            def gitUrl = Configuration.resolveVars("${Configuration.get(Configuration.Parameter.GITHUB_HTML_URL)}/${Configuration.get(REPO)}")

            def userId = isParamEmpty(Configuration.get("userId")) ? '' : Configuration.get("userId")
            registerObject("hooks_view", new ListViewFactory(repoFolder, 'SYSTEM', null, ".*onPush.*|.*onPullRequest.*|.*CutBranch-.*|build|deploy|publish"))

            def pullRequestFreestyleJobDescription = "To finish GitHub Pull Request Checker setup, please, follow the steps below:\n" +
                    "- Manage Jenkins -> Configure System -> Populate 'GitHub Pull Request Builder': usr should have admin privileges, Auto-manage webhooks should be enabled\n" +
                    "- Go to your GitHub repository\n- Click \"Settings\" tab\n- Click \"Webhooks\" menu option\n" +
                    "- Click \"Add webhook\" button\n- Type http://your-jenkins-domain.com/ghprbhook/ into \"Payload URL\" field\n" +
                    "- Select application/x-www-form-urlencoded in \"Content Type\" field\n- Tick \"Let me select individual events\" with \"Issue comments\" and \"Pull requests enabled\" option\n- Click \"Add webhook\" button"
            def pullRequestPipelineJobDescription = "Verify compilation and/or do Sonar PullRequest analysis"


            registerObject("pull_request_job", new PullRequestJobFactory(repoFolder, getOnPullRequestScript(), "onPullRequest-" + Configuration.get(REPO), pullRequestPipelineJobDescription, githubHost, githubOrganization, Configuration.get(REPO), gitUrl))
            registerObject("pull_request_job_trigger", new PullRequestJobFactoryTrigger(repoFolder, "onPullRequest-" + Configuration.get(REPO) + "-trigger", pullRequestFreestyleJobDescription, githubHost, githubOrganization, Configuration.get(REPO), gitUrl))

            def pushJobDescription = "To finish GitHub WebHook setup, please, follow the steps below:\n- Go to your GitHub repository\n- Click \"Settings\" tab\n- Click \"Webhooks\" menu option\n" +
                    "- Click \"Add webhook\" button\n- Type http://your-jenkins-domain.com/github-webhook/ into \"Payload URL\" field\n" +
                    "- Select application/json in \"Content Type\" field\n- Tick \"Send me everything.\" option\n- Click \"Add webhook\" button"
            
            if (!isParamEmpty(this.library)) {
                //load custom library to check inheritance for isTestNGRunner
                logger.debug("load custom library to check inheritance for isTestNGRunner: " + this.library)
                context.library this.library
            }

            def isTestNgRunner = extendsClass([TestNG])
            def isBuildToolDependent = extendsClass([com.zebrunner.jenkins.pipeline.runner.maven.Runner, com.zebrunner.jenkins.pipeline.runner.gradle.Runner, com.zebrunner.jenkins.pipeline.runner.docker.Runner])

            registerObject("push_job", new PushJobFactory(repoFolder, getOnPushScript(), "onPush-" + Configuration.get(REPO), pushJobDescription, githubHost, githubOrganization, Configuration.get(REPO), Configuration.get(BRANCH), gitUrl, userId, isTestNgRunner, zafiraFields))

            def mergeJobDescription = "SCM branch merger job"
            registerObject("merge_job", new MergeJobFactory(repoFolder, getMergeScript(), "CutBranch-" + Configuration.get(REPO), mergeJobDescription, githubHost, githubOrganization, Configuration.get(REPO), gitUrl))

            if (isBuildToolDependent) {
                def buildTool = determineBuildTool()
                def isDockerRunner = false

                if (extendsClass([com.zebrunner.jenkins.pipeline.runner.docker.Runner])) {
                    if (isParamEmpty(getCredentials(githubOrganization + '-docker'))) {
                        updateJenkinsCredentials(githubOrganization + '-docker', 'docker hub creds', Configuration.Parameter.DOCKER_HUB_USERNAME.getValue(), Configuration.Parameter.DOCKER_HUB_PASSWORD.getValue())
                    }

                    isDockerRunner = true
                    registerObject("deploy_job", new DeployJobFactory(repoFolder, getDeployScript(), "deploy", githubHost, githubOrganization, Configuration.get(REPO)))
                    registerObject("publish_job", new PublishJobFactory(repoFolder, getPublishScript(), "publish", githubHost, githubOrganization, Configuration.get(REPO), Configuration.get(BRANCH)))
                }

                registerObject("build_job", new BuildJobFactory(repoFolder, getPipelineScript(), "build", githubHost, githubOrganization, Configuration.get(REPO), Configuration.get(BRANCH), buildTool, isDockerRunner))
            }

            logger.debug("before - factoryRunner.run(dslObjects)")
            factoryRunner.run(dslObjects)
            logger.debug("after - factoryRunner.run(dslObjects)")

        }
    }

    private String getOnPullRequestScript() {
        return "@Library(\'${getPipelineLibrary(this.library)}\')\nimport ${runnerClass}\nnew ${runnerClass}(this).onPullRequest()"
    }

    private String getOnPushScript() {
        return "@Library(\'${getPipelineLibrary(this.library)}\')\nimport ${runnerClass}\nnew ${runnerClass}(this).onPush()"
    }

    protected String getPipelineScript() {
        return "@Library(\'${getPipelineLibrary(this.library)}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).build()"
    }

    protected String getMergeScript() {
        return "@Library(\'${getPipelineLibrary(this.library)}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).mergeBranch()"
    }

    protected String getPublishScript() {
        return "@Library(\'${getPipelineLibrary(this.library)}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).publish()"
    }

    protected String getDeployScript() {
        return "@Library(\'${getPipelineLibrary(this.library)}\')\nimport ${runnerClass};\nnew ${runnerClass}(this).deploy()"
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
            def user = Configuration.get(SCM_USER)
            def token = Configuration.get(SCM_TOKEN)
            def jenkinsUser = !isParamEmpty(Configuration.get("jenkinsUser")) ? Configuration.get("jenkinsUser") : getBuildUser(context.currentBuild)
            if (updateJenkinsCredentials("token_" + jenkinsUser, jenkinsUser + " SCM token", user, token)) {
                logger.info(jenkinsUser + " credentials were successfully registered.")
            } else {
                throw new RuntimeException("Required fields are missing.")
            }
        }
    }

}