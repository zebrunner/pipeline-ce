package com.zebrunner.jenkins.jobdsl.factory.pipeline

import groovy.transform.InheritConstructors

@InheritConstructors
class BuildJobFactory extends PipelineFactory {

    def repoUrl
    def branch
    def isDockerRepo
    def buildTool

    public BuildJobFactory(folder, pipelineScript, jobName, repoUrl, branch, buildTool, isDockerRepo) {
        this.name = jobName
        this.folder = folder
        this.pipelineScript = pipelineScript
        this.repoUrl = repoUrl
        this.branch = branch
        this.buildTool = buildTool
        this.isDockerRepo = isDockerRepo
    }

    def create() {
        logger.info("BuildJobFactory->create")

        def pipelineJob = super.create()

        pipelineJob.with {

            parameters {

                // dockerBuild params
                if (isDockerRepo) {
                    configure stringParam('RELEASE_VERSION', '', 'SemVer-compliant upcoming release or RC version (e.g. 1.13.1 or 1.13.1.RC1)')
                    configure addExtensibleChoice('RELEASE_TYPE', 'gc_RELEASE_TYPE', 'Component release type', 'SNAPSHOT')
                    configure stringParam('DOCKERFILE', 'Dockerfile', 'Relative path to your dockerfile')
                    configure addHiddenParameter('build_tool', '', "${this.buildTool}")
                }

                switch (buildTool.toLowerCase()) {
                    case "maven":
                        configure stringParam('maven_goals', '-U clean install', 'Maven goals to build the project')
                        break
                    case "gradle":
                        configure stringParam('gradle_tasks', 'clean build', 'Gradle tasks to build the project')
                        break
                }

                configure stringParam('branch', branch, "SCM repository branch containing sources for component build")
                configure booleanParam('fork', false, "Reuse forked repository.")
                configure addExtensibleChoice('BuildPriority', "gc_BUILD_PRIORITY", "Priority of execution. Lower number means higher priority", "3")
                configure stringParam('email_list', "", 'List of Users to be emailed after the build')
                configure addHiddenParameter('repoUrl', 'repository url', repoUrl)
            }

        }

        return pipelineJob
    }
}
