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
        def repo = ""
        if ("qaprosoft".equals(this.folder) || "".equals(this.folder)) {
            repo = "carina-demo"
        }
        def org = "qaprosoft"
        if (!this.folder.isEmpty()) {
            org = this.folder
        }

        pipelineJob.with {
            parameters {
                configure stringParam('scmHost', 'github.com', 'Source Control Management host')
                configure stringParam('scmOrg', org, 'Source Control Management organization')
                configure stringParam('repo', repo, 'Repository for scanning')
                configure stringParam('branch', 'master', 'SCM repository branch to run against')
                configure stringParam('scmUser', '', 'SCM user')
                configure stringParam('scmToken', '', 'CSM token with read permissions')
                configure addExtensibleChoice('pipelineLibrary', "gc_PIPELINE_LIBRARY", "Groovy JobDSL/Pipeline library, for example: https://github.com/zebrunner/pipeline-ce/releases", "Zebrunner-CE")
                configure addExtensibleChoice('runnerClass', "gc_RUNNER_CLASS", "Pipeline runner class", "com.zebrunner.jenkins.pipeline.runner.maven.TestNG")
                configure addHiddenParameter('zafiraFields', '', '')
                configure addHiddenParameter('userId', '', '2')
            }
        }
        return pipelineJob
    }

}