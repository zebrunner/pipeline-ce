package com.zebrunner.jenkins.jobdsl.factory.pipeline

import groovy.transform.InheritConstructors
import com.cloudbees.plugins.credentials.common.StandardCredentials

@InheritConstructors
public class RegisterRepositoryJobFactory extends PipelineFactory {
    public RegisterRepositoryJobFactory(folder, pipelineScript, name, jobDesc) {
        this.folder = folder
        this.pipelineScript = pipelineScript
        this.name = name
        this.description = jobDesc
    }

    def create() {
        logger.info("RegisterRepositoryJobFactory->create")
        def pipelineJob = super.create()

        pipelineJob.with {
            parameters {
                configure addExtensibleChoice('scmType', "gc_GIT_TYPE", "Version control system type", "github")
                configure stringParam('repoUrl', "https://github.com/zebrunner/carina-demo.git", 'Repository for scanning')
                configure stringParam('branch', 'main', "SCM repository branch to run against (use 'refs/tags/1.0' to clone by tag)")
                stringParam('node_label', '', 'Optional agent label. Leave empty to use the configured node.')
                credentialsParam('scmCredentialsId') {
                    type(StandardCredentials.class.name)
                    defaultValue('')
                    required(false)
                    description('Optional credential for repository checkout: a GitHub App or username/password credential containing a PAT.')
                }
                configure addExtensibleChoice('pipelineLibrary', "gc_PIPELINE_LIBRARY", "Groovy JobDSL/Pipeline library, for example: https://github.com/zebrunner/pipeline-ce/releases", "Zebrunner-CE")
                configure addExtensibleChoice('runnerClass', "gc_RUNNER_CLASS", "Pipeline runner class", "com.zebrunner.jenkins.pipeline.runner.maven.TestNG")
                configure addHiddenParameter('userId', '', '2')
            }
        }
        return pipelineJob
    }

}
