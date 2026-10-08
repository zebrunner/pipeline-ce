package com.zebrunner.jenkins.jobdsl.factory.pipeline

import groovy.transform.InheritConstructors

@InheritConstructors
class BuildJobFactory extends PipelineFactory {

    def repoUrl
    def branch
    def isDockerRepo
    def isMavenRepo = false
    def scmCredentialsId = ''

    public BuildJobFactory(folder, pipelineScript, jobName, desc, repoUrl, branch, isDockerRepo, scmCredentialsId = '', isMavenRepo = false) {
        this.name = jobName
        this.description = desc
        this.folder = folder
        this.pipelineScript = pipelineScript
        this.repoUrl = repoUrl
        this.branch = branch
        this.isDockerRepo = isDockerRepo
        this.isMavenRepo = isMavenRepo
        this.scmCredentialsId = scmCredentialsId
    }

    def create() {
        logger.info("BuildJobFactory->create")

        def pipelineJob = super.create()

        pipelineJob.with {

            parameters {

                // dockerBuild params
                if (isDockerRepo) {
                    configure stringParam('RELEASE_VERSION', '', 'SemVer-compliant upcoming release or RC version (e.g. 1.13.1 or 1.13.1.RC1)')
                    configure choiceParam('RELEASE_TYPE', ['SNAPSHOT', 'RELEASE_CANDIDATE', 'RELEASE'], 'Component release type')
                    configure stringParam('DOCKERFILE', 'Dockerfile', 'Relative path to your dockerfile')
                }
                
                configure stringParam('branch', branch, "SCM repository branch containing sources for component build")
                configure stringParam('goals', '', 'Extra build tool goals to build the project')
                if (isMavenRepo) {
                    stringParam('node_label', '', 'Optional agent label. Leave empty to use the configured node.')
                }
                configure addHiddenParameter('repoUrl', 'repository url', repoUrl)
                configure addHiddenParameter('scmCredentialsId', '', scmCredentialsId)
            }

        }

        return pipelineJob
    }
}
