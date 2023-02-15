package com.zebrunner.jenkins.jobdsl.factory.pipeline

import static com.zebrunner.jenkins.Utils.*
import org.testng.xml.XmlSuite
import groovy.transform.InheritConstructors

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
                configure stringParam('branch', 'master', 'SCM repository branch to run against')
                configure stringParam('scmUser', '', 'SCM user')
                configure stringParam('scmToken', '', 'CSM token with read permissions')
                configure addExtensibleChoice('pipelineLibrary', "gc_PIPELINE_LIBRARY", "Groovy JobDSL/Pipeline library, for example: https://github.com/zebrunner/pipeline-ce/releases", "Zebrunner-CE")
                configure addExtensibleChoice('runnerClass', "gc_RUNNER_CLASS", "Pipeline runner class", "com.zebrunner.jenkins.pipeline.runner.maven.TestNG")
                configure addHiddenParameter('userId', '', '2')
            }
        }
        return pipelineJob
    }

}