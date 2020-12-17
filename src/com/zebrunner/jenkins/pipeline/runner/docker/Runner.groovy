package com.zebrunner.jenkins.pipeline.runner.docker

import com.zebrunner.jenkins.pipeline.runner.AbstractRunner
import com.zebrunner.jenkins.pipeline.Configuration

import static com.zebrunner.jenkins.Utils.*
import static com.zebrunner.jenkins.pipeline.Executor.*

class Runner extends AbstractRunner {

    private static def SEMVER_REGEX = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)/
    private static def SEMVER_REGEX_RC = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)\.RC([1-9]\d*)/

    // protected def runnerClass
    protected def registry
    protected def registryCreds
    protected def releaseVersion
    protected def branch
    protected def dockerFile

    protected def goals

    public Runner(context) {
        super(context)

        // this.runnerClass = "com.zebrunner.jenkins.pipeline.runner.docker.Runner"
        this.registry = "${this.organization}/${this.repo}"
        this.registryCreds = "${this.organization}-docker"

        this.branch = Configuration.get("branch")

        this.dockerFile = Configuration.get("DOCKERFILE")

        this.goals = Configuration.get("goals")
        
        if (Configuration.get("pro")?.toBoolean()) {
            Configuration.set(REPO_URL, Configuration.get(REPO_URL).replace("zebrunner/ce", "zebrunner/pro"))
        }
        Configuration.get(REPO_URL)
    }

    @Override
    public void onPush() {
        def version = Configuration.get("branch") + "-latest"
        
        context.node('docker') {
            context.timestamps {
                logger.info('DockerRunner->onPush')
                try {
                    getScm().clonePush()
                    buildSources(false)
                    
                    def image = context.dockerDeploy.build(version, registry)
                    context.dockerDeploy.push(image, registryCreds)
                    context.dockerDeploy.clean(image)
                } catch (Exception e) {
                    context.error("Something went wrong while building and pushing the docker image. \n" + printStackTrace(e))
                } finally {
                    clean()
                }
            }
        }
    }

    @Override
    public void onPullRequest() {
        def pr_number = configuration.get("pr_number")

        def version = "${pr_number}"
        context.currentBuild.setDisplayName(Configuration.get("BUILD_NUMBER") + "|" + version)
        context.node('docker') {
            context.timestamps {
                logger.info('DockerRunner->onPullRequest')
                try {
                    getScm().clonePR()
                    buildSources(true)
                    
                    def image = context.dockerDeploy.build(version, registry)
                    context.dockerDeploy.clean(image)
                } catch (Exception e) {
                    context.error("Something went wrong while building the docker image. \n" + printStackTrace(e))
                } finally {
                    clean()
                }
            }
        }
    }

    @Override
    public void build() {
        def releaseType = Configuration.get("RELEASE_TYPE")
        def releaseVersion = Configuration.get("RELEASE_VERSION")

        def buildNumber = Configuration.get("BUILD_NUMBER")

        // do semantic versioning verifications
        if (!(releaseVersion ==~ "${SEMVER_REGEX}") && !(releaseVersion ==~ "${SEMVER_REGEX_RC}")) {
            context.error("Upcoming release version should be a valid SemVer-compliant release or RC version! Visit for details: https://semver.org/")
        }

        def releaseTagFull
        def releaseTagMM

        def releaseVersionMM = releaseVersion.split('\\.')[0] + '.' + releaseVersion.split('\\.')[1]
        logger.info("releaseVersionMM: " + releaseVersionMM)

        // following block is used to construct release tags
        // RELEASE_TAG_FULL is used to fully identify this specific build
        // RELEASE_TAG_MM is used to tag this specific build as latest MAJOR.MINOR version
        if ("SNAPSHOT".equals(releaseType)) {
            releaseTagFull = "${releaseVersion}.${buildNumber}-SNAPSHOT"
            releaseTagMM = "${releaseVersionMM}-SNAPSHOT"
        } else if ("RELEASE_CANDIDATE".equals(releaseType)) {
            if (!"develop".equals(this.branch) || !(releaseVersion ==~ "${SEMVER_REGEX_RC}")) {
                context.error("Release Candidate can only be built from develop branch (actual: ${this.branch}) and should be labeled with valid RC version, e.g. 1.13.1.RC1 (actual: ${releaseVersion})")
            }
            releaseTagFull = releaseVersion
            releaseTagMM = "${releaseVersionMM}-SNAPSHOT"
        } else if ("RELEASE".equals(releaseType)) {
            if (!"master".equals(this.branch) || !(releaseVersion ==~ "${SEMVER_REGEX_RC}")) {
                context.error("Release can only be built from master branch (actual: ${this.branch}) and should be labeled with valid release version, e.g. 1.13.1 (actual: ${releaseVersion})")
            }
            releaseTagFull = releaseVersion
            releaseTagMM = releaseVersionMM
        }

        logger.debug("releaseTagFull: " + releaseTagFull)
        logger.debug("releaseTagMM: " + releaseTagMM)

        context.currentBuild.setDisplayName(releaseTagFull)

        context.node('docker') {
            context.timestamps {
                logger.info('DockerRunner->build')
                try {
                    getScm().clone()
                    buildSources(false)

                    def image = context.dockerDeploy.build(releaseTagFull, registry, dockerFile)
                    context.dockerDeploy.push(image, registryCreds)
                    // push the same image using different tag
                    context.dockerDeploy.push(image, registryCreds, releaseTagMM)
                    context.dockerDeploy.clean(image)
                } catch(Exception e) {
                    context.error("Something went wrond while building and pushing the image. \n" + printStackTrace(e))
                } finally {
                    clean()
                }
            }
        }
    }
    
    protected void buildSources(isPullRequest = false) {
        // redefine default maven/gradle goals if nothing provided as goals
        if (isParamEmpty(this.goals)) {
            
            if (isMaven()) {
                this.goals = "-U clean install"
            }
            
            if (isGradle()) {
                this.goals = "clean build"
            }
        }
        
        // append sonar related goals if needed
        goals += sc.getGoals(isPullRequest)
        logger.debug("goals: " + this.goals)
        
        if (isGradle()) {
            context.gradleBuild(this.goals)
        } else if (isMaven()) {
            context.mavenBuild(this.goals, getMavenSettings())
        }
        
    }
 
    private def isMaven() {
        return context.fileExists("pom.xml")
    }
    
    private def isGradle() {
        return context.fileExists("build.gradle")
    }

}