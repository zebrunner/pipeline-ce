package com.zebrunner.jenkins

import com.zebrunner.jenkins.pipeline.Configuration
import com.zebrunner.jenkins.pipeline.tools.scm.ISCM
import com.zebrunner.jenkins.pipeline.tools.scm.github.GitHub
import groovy.json.JsonOutput

public class FactoryRunner {
    private def context
    private ISCM scmClient

    private final def FACTORY_TARGET = "pipeline-ce/src/com/zebrunner/jenkins/Factory.groovy"
    private def additionalClasspath = "pipeline-ce/src"

    private def isPrepared = false

    public FactoryRunner(context) {
        this.context = context
        this.scmClient = new GitHub(context)
    }

    /*
     * Export dslObjects into factories.json and start Factory.groovy as JobDSL script to regenerate jenkins items (jobs, views etc)
     * removedConfigFilesAction, removedJobAction and removedViewAction are set to 'IGNORE' by default
     */

    public void run(dslObjects) {
        run(dslObjects, 'IGNORE', 'IGNORE', 'IGNORE')
    }

    /*
     * Export dslObjects into factories.json and start Factory.groovy as JobDSL script to regenerate jenkins items (jobs, views etc)
     */

    public void run(dslObjects, removedConfigFilesAction, removedJobAction, removedViewAction) {
        if (!this.isPrepared) {
            prepare()
        }

        context.writeFile file: "factories.json", text: JsonOutput.toJson(dslObjects)
        context.jobDsl additionalClasspath: this.additionalClasspath,
                removedConfigFilesAction: removedConfigFilesAction,
                removedJobAction: removedJobAction,
                removedViewAction: removedViewAction,
                targets: FACTORY_TARGET,
                ignoreExisting: false
    }

    public void setDslClasspath(additionalClasspath) {
        this.additionalClasspath = additionalClasspath
    }

    public void prepare() {
        scmClient.clone(context.env[Configuration.ZEBRUNNER_PIPELINE], context.env[Configuration.ZEBRUNNER_VERSION], "pipeline-ce")
        this.isPrepared = true
    }
}
