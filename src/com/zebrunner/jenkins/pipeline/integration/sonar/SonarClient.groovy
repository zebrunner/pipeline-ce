package com.zebrunner.jenkins.pipeline.integration.sonar

import com.zebrunner.jenkins.pipeline.integration.HttpClient
import com.zebrunner.jenkins.pipeline.Configuration

import static com.zebrunner.jenkins.Utils.*

class SonarClient extends HttpClient {

    private String serviceUrl
    private String token

    SonarClient(context) {
        super(context)
        this.serviceUrl = context.env[Configuration.SONAR_URL]
        this.token = context.env[Configuration.SONAR_TOKEN]
    }

    public String getGoals(isPullRequest=false) {
        def goals = ""
        if (isParamEmpty(this.serviceUrl)) {
            logger.warn("The url for the sonarqube server is not configured, sonarqube scan will be skipped!")
            return goals
        }
        
        if (isParamEmpty(this.token)) {
            logger.warn("Sonarqube token is not configured, sonarqube scan will be skipped!")
            return goals
        }

        if (!isAvailable()) {
            logger.warn("The sonarqube ${this.serviceUrl} server is not available, sonarqube scan will be skipped!")
            return goals
        }
        goals = " \
                  -Dsonar.host.url=${this.serviceUrl} \
                  -Dsonar.login=${this.token} \
                  -Dsonar.log.level=${this.logger.pipelineLogLevel} \
                  -Dsonar.junit.reportPaths=target/surefire-reports "

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

        // Configure JVM memory settings for Sonar analysis
        // These can be overridden via environment variables to prevent OOM errors on large projects
        def sonarJvmOpts = context.env['SONAR_JVM_OPTS'] ?: "-Xms1g -Xmx4g -XX:+UseG1GC"
        def sonarScannerOpts = context.env['SONAR_SCANNER_JVM_OPTS'] ?: "-Xms1g -Xmx4g -XX:+UseG1GC"

        // Determine at run-time if we use maven or gradle.
        def extraGoals = ""
        def envVars = ""

        if (isMaven()) {
            extraGoals = " sonar:sonar"
            // Set MAVEN_OPTS for Maven execution to prevent heap space errors
            envVars = "MAVEN_OPTS='${sonarJvmOpts}' "
        }
        // Gradle has higher priority!
        if (isGradle()) {
            extraGoals = " sonarqube"
        }

        // Add SONAR_SCANNER_OPTS for Sonar Scanner CLI (applicable to both Maven and Gradle)
        envVars += "SONAR_SCANNER_OPTS='${sonarScannerOpts}' "

        goals = envVars + goals + extraGoals

        return goals
    }

    /**
     * Reads the analysis task produced by the last sonar:sonar run (target/sonar/report-task.txt),
     * waits for SonarQube to finish processing it on the server, then returns the pull request
     * quality gate result. This lets the pipeline publish the gate to the SCM itself instead of
     * relying on server-side PR decoration (e.g. the Community Branch Plugin), which can break
     * when the GitHub API changes.
     *
     * @param reportTaskPath path to the scanner's report-task.txt (default target/sonar/report-task.txt)
     * @return map [state: 'success'|'failure'|null, status: 'OK'|'ERROR'|'NONE', dashboardUrl: '...'].
     *         state == null means Sonar is not configured or the result is unavailable; callers should skip.
     */
    public Map getQualityGateStatus(reportTaskPath = "target/sonar/report-task.txt") {
        def result = [state: null, status: "NONE", dashboardUrl: ""]

        if (isParamEmpty(this.serviceUrl) || isParamEmpty(this.token)) {
            logger.warn("SonarQube url/token not configured, skipping quality gate status publishing.")
            return result
        }
        if (!context.fileExists(reportTaskPath)) {
            logger.warn("Sonar report task file not found: ${reportTaskPath}, skipping quality gate status publishing.")
            return result
        }

        def report = context.readProperties file: reportTaskPath
        def ceTaskId = report['ceTaskId']
        result.dashboardUrl = report['dashboardUrl']
        if (isParamEmpty(ceTaskId)) {
            logger.warn("ceTaskId is missing in ${reportTaskPath}, skipping quality gate status publishing.")
            return result
        }

        // SonarQube analysis tokens authenticate via Basic auth (token as username, empty password).
        def authHeader = "Basic " + "${this.token}:".bytes.encodeBase64().toString()
        def headers = [[name: 'Authorization', value: authHeader, maskValue: true]]

        // Wait for the compute engine to finish processing the submitted report (~5 min max).
        def analysisId = ""
        for (int i = 0; i < 60; i++) {
            def task = sendRequestFormatted([customHeaders     : headers,
                                             contentType       : 'APPLICATION_JSON',
                                             httpMode          : 'GET',
                                             validResponseCodes: '200',
                                             url               : this.serviceUrl + "/api/ce/task?id=${ceTaskId}"])
            def status = task?.task?.status
            if (status in ["SUCCESS", "FAILED", "CANCELED"]) {
                analysisId = task?.task?.analysisId
                break
            }
            context.sleep(time: 5, unit: 'SECONDS')
        }
        if (isParamEmpty(analysisId)) {
            logger.warn("Could not resolve SonarQube analysisId for task ${ceTaskId}, skipping quality gate status publishing.")
            return result
        }

        def qg = sendRequestFormatted([customHeaders     : headers,
                                       contentType       : 'APPLICATION_JSON',
                                       httpMode          : 'GET',
                                       validResponseCodes: '200',
                                       url               : this.serviceUrl + "/api/qualitygates/project_status?analysisId=${analysisId}"])
        def gate = qg?.projectStatus?.status   // OK | WARN | ERROR | NONE
        result.status = gate ?: "NONE"
        result.state = (gate == "OK") ? "success" : ((gate == "ERROR" || gate == "WARN") ? "failure" : null)
        logger.info("SonarQube quality gate for analysis ${analysisId}: ${result.status} -> github state '${result.state}'")
        return result
    }

    private boolean isAvailable() {
        def parameters = [contentType        : 'APPLICATION_JSON',
                          httpMode           : 'GET',
                          validResponseCodes : '200',
                          url                : this.serviceUrl + '/api/system/status']
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