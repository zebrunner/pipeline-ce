package com.zebrunner.jenkins.pipeline.integration.sonar

import com.zebrunner.jenkins.pipeline.integration.HttpClient
import com.zebrunner.jenkins.pipeline.Configuration

import static com.zebrunner.jenkins.Utils.*

class SonarClient extends HttpClient {

    private String serviceUrl

    SonarClient(context) {
        super(context)
        serviceUrl = context.env.getEnvironment().get("SONAR_URL")
    }

    public String getGoals(isPullRequest=false) {
        def goals = ""
        if (isParamEmpty(serviceUrl)) {
            logger.warn("The url for the sonarqube server is not configured, sonarqube scan will be skipped!")
            return goals
        }

        if (!isAvailable()) {
            logger.warn("The sonarqube ${this.serviceUrl} server is not available, sonarqube scan will be skipped!")
            return goals
        }
        goals = " -Dsonar.host.url=${this.serviceUrl} -Dsonar.log.level=${this.logger.pipelineLogLevel} -Dsonar.jacoco.reportPaths=target/site/jacoco/jacoco.xml -Dsonar.junit.reportPaths=target/surefire-reports "

        if (isPullRequest) {
            // goals needed to decorete pr with sonar analysis

            def gitType = Configuration.get("scmType")
            switch (gitType) {
                case "github":
                    goals += " -Dsonar.pullrequest.provider=Github \
                               -Dsonar.pullrequest.github.repository=${Configuration.get("pr_repository")}"
                    break
                case "gitlab":
                    goals += " -Dsonar.pullrequest.gitlab.repositorySlug=${Configuration.get("pr_repository")} \
                               -Dsonar.scm.revision=${Configuration.get("pr_sha")} \
                               -Dsonar.pullrequest.provider=GitlabServer"
                    break
                case "bitbucket":
                    goals += " -Dsonar.pullrequest.bitbucket.repositorySlug=${Configuration.get("pr_repository")} \
                               -Dsonar.pullrequest.bitbucket.projectKey=${Configuration.get("pr_sha")} \
                               -Dsonar.pullrequest.provider=BitbucketServer"
                    break
                default:
                    throw new RuntimeException("Unsuported source control management: ${gitType}!")
            }
            
            goals += " -Dsonar.pullrequest.key=${Configuration.get("pr_number")} \
                    -Dsonar.pullrequest.branch=${Configuration.get("pr_source_branch")} \
                    -Dsonar.pullrequest.base=${Configuration.get("pr_target_branch")}"
        } else {
            goals += " -Dsonar.projectVersion=${Configuration.get("BUILD_NUMBER")} -Dsonar.branch.name=${Configuration.get("branch")}"
        }
        
        // Determine at run-time if we use maven or gradle.
        def extraGoals = ""
        if (isMaven()) {
            extraGoals = " sonar:sonar"
        }
        // Gradle has higher priority!
        if (isGradle()) {
            extraGoals = " sonarqube"
        }
        goals += extraGoals

        return goals
    }

    private boolean isAvailable() {
        def parameters = [contentType        : 'APPLICATION_JSON',
                          httpMode           : 'GET',
                          validResponseCodes : '200',
                          url                : serviceUrl + '/api/system/status']
        return "UP".equals(sendRequestFormatted(parameters)?.get("status"))
    }

    private def isMaven() {
        def files = context.findFiles glob: '**/pom.xml'
        boolean res = files.length > 0
        logger.debug("isMaven: " + res)
        return res
    }
    
    private def isGradle() {
        return context.fileExists("build.gradle")
    }

}