package com.zebrunner.jenkins.pipeline.runner

import com.zebrunner.jenkins.BaseObject
import com.zebrunner.jenkins.pipeline.Configuration
import com.zebrunner.jenkins.pipeline.tools.scm.github.GitHub
import com.zebrunner.jenkins.pipeline.tools.scm.gitlab.Gitlab
import com.zebrunner.jenkins.pipeline.tools.scm.bitbucket.BitBucket
import com.zebrunner.jenkins.pipeline.integration.sonar.SonarClient

import static com.zebrunner.jenkins.Utils.*
import static com.zebrunner.jenkins.pipeline.Executor.*

public abstract class AbstractRunner extends BaseObject {
    SonarClient sc
    
    public AbstractRunner(context) {
        super(context)
        sc = new SonarClient(context)

        setDisplayNameTemplate('#${BUILD_NUMBER}|${Configuration.get("branch")}')
    }

    //Methods
    abstract public void build()

    //Events
    abstract public void onPush()

    abstract public void onPullRequest()

    /*
     * Execute custom pipeline/jobdsl steps from Jenkinsfile
     */
    
    protected void jenkinsFileScan() {
        def isCustomPipelineEnabled = getToken(Configuration.CREDS_CUSTOM_PIPELINE)

        if (!isCustomPipelineEnabled) {
            logger.warn("Custom pipeline execution is not enabled")
            return
        }

        if (!context.fileExists('Jenkinsfile')) {
            logger.warn("Jenkinsfile doesn't exist in your repository")
            return
        }

        context.stage('Jenkinsfile Stage') {
            context.script {
                context.jobDsl targets: 'Jenkinsfile'
            }
        }
    }

    /*
     * Determined current organization folder by job name
     */

    /*
     * Get token key from Jenkins credentials based on organization
     *
     * @param tokenName Jenkins credentials id
     * @return token value
     */

    protected def getToken(tokenName) {
        def tokenValue = ""

        if (!isParamEmpty(this.organization)) {
            tokenName = "${this.organization}" + "-" + tokenName
        }

        if (getCredentials(tokenName)) {
            context.withCredentials([context.usernamePassword(credentialsId: tokenName, usernameVariable: 'KEY', passwordVariable: 'VALUE')]) {
                tokenValue = context.env.VALUE
            }
        }
        logger.debug("tokenName: ${tokenName}; tokenValue: ${tokenValue}")
        return tokenValue
    }

    /*
     * Get username and password from Jenkins credentials based on organization
     * 
     * @param tokenName Jenkins credentials id
     * @return name and password
     */

    protected def getUserCreds(tokenName) {
        def name = ""
        def password = ""

        if (!isParamEmpty(this.organization)) {
            tokenName = "${this.organization}" + "-" + tokenName
        }

        if (getCredentials(tokenName)) {
            context.withCredentials([context.usernamePassword(credentialsId: tokenName, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                name = context.env.USERNAME
                password = context.env.PASSWORD
            }
        }
        logger.debug("tokenName: ${tokenName}; name: ${name}; password: ${password}")
        return [name, password]
    }

    /*
     * set DslClasspath to support custom JobDSL logic
     */

    protected void setDslClasspath(additionalClasspath) {
        factoryRunner.setDslClasspath(additionalClasspath)
    }


}
