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
	
	public Runner(context) {
		super(context)
		runnerClass = "com.zebrunner.jenkins.pipeline.runner.docker.Runner"
		registry = "${this.organization}/${this.repo}"
		registryCreds = "${this.organization}-docker"

		releaseType = Configuration.get("RELEASE_TYPE")
		// SNAPSHOT - RELEASE_VERSION.BUILD_NUMBER-SNAPSHOT
		// RELEASE and RELEASE_CANDIDATE - just RELEASE_VERSION
		releaseVersion = Configuration.get("RELEASE_VERSION")
        
        if (!(releaseVersion ==~ "${SEMVER_REGEX}") && !(releaseVersion ==~ "${SEMVER_REGEX_RC}")) {
            throw new RuntimeException("Upcoming release version should be a valid SemVer-compliant release or RC version! Visit for details: https://semver.org/")
        }
        
		if ("SNAPSHOT".equals(releaseType)) {
			releaseVersion = "${Configuration.get("RELEASE_VERSION")}.${Configuration.get("BUILD_NUMBER")}-SNAPSHOT"
		}

		buildTool = Configuration.get("build_tool")
		dockerFile = Configuration.get("DOCKERFILE")
		context.currentBuild.setDisplayName(releaseVersion)
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
					context.currentBuild.setDisplayName(releaseVersion)
					context.dockerDeploy(releaseVersion, registry, registryCreds, dockerFile)
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