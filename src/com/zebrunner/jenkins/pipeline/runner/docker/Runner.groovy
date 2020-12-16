package com.zebrunner.jenkins.pipeline.runner.docker

import com.zebrunner.jenkins.pipeline.runner.AbstractRunner
import com.zebrunner.jenkins.pipeline.Configuration
import com.zebrunner.jenkins.Utils

import static com.zebrunner.jenkins.pipeline.Executor.*

class Runner extends AbstractRunner {
    
    private static def SEMVER_REGEX = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)/
    private static def SEMVER_REGEX_RC = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)\.RC([1-9]\d*)/
	
	protected def runnerClass
	protected def registry
	protected def registryCreds
	protected def releaseVersion
	protected def releaseType
	protected def dockerFile
  	protected def buildTool
      
    protected def branch
	
	public Runner(context) {
		super(context)
		
        this.runnerClass = "com.zebrunner.jenkins.pipeline.runner.docker.Runner"
		this.registry = "${this.organization}/${this.repo}"
		this.registryCreds = "${this.organization}-docker"

		this.releaseType = Configuration.get("RELEASE_TYPE")
		this.releaseVersion = Configuration.get("RELEASE_VERSION")
        
        this.branch = Configuration.get("branch")
        
		buildTool = Configuration.get("build_tool")
		dockerFile = Configuration.get("DOCKERFILE")
		
	}

	@Override
	public void onPush() {
		context.node('docker') {
			context.timestamps {
				logger.info('DockerRunner->onPush')
				try {
					getScm().clonePush()
					context.dockerDeploy(releaseVersion, registry, registryCreds)
				} catch (Exception e) {
					logger.error("Something went wrong while pushing the docker image. \n" + Utils.printStackTrace(e))
					context.currentBuild.result = BuildResult.FAILURE
				} finally {
					clean()
				}
			}
		}
	}

	@Override
	public void onPullRequest() {
		context.node('docker') {
			context.timestamps {
				logger.info('DockerRunner->onPullRequest')
				try {
					context.currentBuild.setDisplayName(releaseVersion)
					getScm().clonePR()
					def image = context.dockerDeploy.build(releaseVersion, registry)
					context.dockerDeploy.clean(image)
				} catch (Exception e) {
					logger.error("Something went wrong while building the docker image. \n" + Utils.printStackTrace(e))
					context.currentBuild.result = BuildResult.FAILURE
				} finally {
					clean()
				}
			}
		}
	}

	@Override
	public void build() {
        // do semantic versioning verifications
        if (!(this.releaseVersion ==~ "${SEMVER_REGEX}") && !(this.releaseVersion ==~ "${SEMVER_REGEX_RC}")) {
            context.error("Upcoming release version should be a valid SemVer-compliant release or RC version! Visit for details: https://semver.org/")
        }
        
        def releaseTagFull
        def releaseTagMM

        def releaseVersionMM = this.releaseVersion.split('\\.')[0] + '.' + this.releaseVersion.split('\\.')[1]
        logger.info("releaseVersionMM: " + releaseVersionMM)
        
        def buildNumber = Configuration.get("BUILD_NUMBER")
        
        // following block is used to construct release tags
        // RELEASE_TAG_FULL is used to fully identify this specific build
        // RELEASE_TAG_MM is used to tag this specific build as latest MAJOR.MINOR version
        if ("SNAPSHOT".equals(this.releaseType)) {
            releaseTagFull = "${this.releaseVersion}.${buildNumber}-SNAPSHOT"
            releaseTagMM = "${releaseVersionMM}-SNAPSHOT"
        } else if ("RELEASE_CANDIDATE".equals(this.releaseType)) {
            if (!"develop".equals(this.branch) || !(this.releaseVersion ==~ "${SEMVER_REGEX_RC}")) {
                context.error("Release Candidate can only be built from develop branch (actual: ${this.branch}) and should be labeled with valid RC version, e.g. 1.13.1.RC1 (actual: ${this.releaseVersion})")
            }
            releaseTagFull = this.releaseVersion
            releaseTagMM = "${releaseVersionMM}-SNAPSHOT"
        } else if ("RELEASE".equals(this.releaseType)) {
            if (!"master".equals(this.branch) || !(this.releaseVersion ==~ "${SEMVER_REGEX_RC}")) {
                context.error("Release can only be built from master branch (actual: ${this.branch}) and should be labeled with valid release version, e.g. 1.13.1 (actual: ${this.releaseVersion})")
            }
            releaseTagFull = this.releaseVersion
            releaseTagMM = releaseVersionMM
        }

        logger.info("releaseTagFull: " + releaseTagFull)
        logger.info("releaseTagMM: " + releaseTagMM)
        
        context.currentBuild.setDisplayName(releaseTagFull)
        
		context.node('docker') {
			context.timestamps {
				logger.info('DockerRunner->build')
				try {
					setDisplayNameTemplate("#${releaseVersion}|${Configuration.get('branch')}")
					currentBuild.displayName = getDisplayName()
					getScm().clone()

					context.stage("${this.buildTool} build") {
						switch (buildTool.toLowerCase()) {
							case 'maven':
								context.mavenBuild(Configuration.get('maven_goals'), getMavenSettings())
								break
							case 'gradle':
								context.gradleBuild('./gradlew ' + Configuration.get('gradle_tasks'))
								break
						}
					}

				} catch (Exception e) {
					logger.error("Something went wrond while building the project. \n" + Utils.printStackTrace(e))
					context.currentBuild.result = BuildResult.FAILURE
				}

				try {
					context.dockerDeploy(releaseTagFull, registry, registryCreds, dockerFile)
                    context.dockerDeploy(releaseTagMM, registry, registryCreds, dockerFile)
				} catch(Exception e) {
					logger.error("Something went wrond while pushin the image. \n" + Utils.printStackTrace(e))
					context.currentBuild.result = BuildResult.FAILURE
				} finally {
					clean()
				}
			}
		}
	}

}