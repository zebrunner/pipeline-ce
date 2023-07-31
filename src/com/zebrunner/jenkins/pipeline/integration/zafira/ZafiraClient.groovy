package com.zebrunner.jenkins.pipeline.integration.zafira

import com.zebrunner.jenkins.pipeline.integration.HttpClient
import groovy.json.JsonBuilder
import static com.zebrunner.jenkins.Utils.*
import com.zebrunner.jenkins.pipeline.Configuration

/*
 * Prerequisites: valid REPORTING_SERVER_HOSTNAME and REPORTING_SERVER_ACCESS_TOKEN values as env vars
 */

class ZafiraClient extends HttpClient {

    private String serviceURL
    private String refreshToken
    private String authToken
    private long tokenExpTime

    public ZafiraClient(context) {
        super(context)
        this.serviceURL = context.env.REPORTING_SERVER_HOSTNAME 
        this.refreshToken = context.env.REPORTING_SERVER_ACCESS_TOKEN
    }

    public def abortTestRun(uuid, failureReason) {
        if (!isZafiraConnected()) {
            return
        }
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder comment: failureReason

        logger.debug("REQUEST: " + jsonBuilder.toPrettyString())
        String requestBody = jsonBuilder.toString()
        jsonBuilder = null

        def parameters = [customHeaders     : [[name: 'Authorization', value: "${authToken}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'POST',
                          requestBody       : requestBody,
                          validResponseCodes: "200:500",
                          url               : this.serviceURL + "/api/reporting/api/tests/runs/abort?ciRunId=${uuid}"]
        return sendRequestFormatted(parameters)
    }

    public def sendEmail(uuid, emailList, filter) {
        if (!isZafiraConnected()) {
            return
        }
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder recipients: emailList

        logger.debug("REQUEST: " + jsonBuilder.toPrettyString())
        String requestBody = jsonBuilder.toString()
        jsonBuilder = null

        def parameters = [customHeaders     : [[name: 'Authorization', value: "${authToken}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'POST',
                          requestBody       : requestBody,
                          validResponseCodes: "200:401",
                          url               : this.serviceURL + "/api/reporting/api/tests/runs/${uuid}/email?filter=${filter}"]
        return sendRequest(parameters)
    }
    
    public def sendFailureEmail(uuid, emailList, suiteOwner, suiteRunner) {
        if (!isZafiraConnected()) {
            return
        }
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder recipients: emailList

        logger.debug("REQUEST: " + jsonBuilder.toPrettyString())

        String requestBody = jsonBuilder.toString()
        jsonBuilder = null
        def parameters = [customHeaders     : [[name: 'Authorization', value: "${authToken}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'POST',
                          requestBody       : requestBody,
                          validResponseCodes: "200:401",
                          url               : this.serviceURL + "/api/reporting/api/tests/runs/${uuid}/emailFailure?suiteOwner=${suiteOwner}&suiteRunner=${suiteRunner}"]
        return sendRequest(parameters)
    }

    public def exportZafiraReport(uuid) {
        if (!isZafiraConnected()) {
            return
        }
        def parameters = [customHeaders     : [[name: 'Authorization', value: "${authToken}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'GET',
                          validResponseCodes: "200:500",
                          url               : this.serviceURL + "/api/reporting/api/tests/runs/${uuid}/export"]

        return sendRequest(parameters)
    }

    public def getTestRunByCiRunId(uuid) {
        if (!isZafiraConnected()) {
            return
        }
        def parameters = [customHeaders     : [[name: 'Authorization', value: "${authToken}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'GET',
                          validResponseCodes: "200:404",
                          url               : this.serviceURL + "/api/reporting/api/tests/runs?ciRunId=${uuid}"]

        return sendRequestFormatted(parameters)
    }

    public def getTestRunResults(testRunId) {
        if (!isZafiraConnected()) {
            return
        }
        def parameters = [customHeaders     : [[name: 'Authorization', value: "${authToken}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'GET',
                          validResponseCodes: "200:404",
                          url               : this.serviceURL + "/api/reporting/api/tests/runs/${testRunId}/results"]

        return sendRequestFormatted(parameters)
    }
    
    // we don't provide projectId to collect consolidated results from all projects. reporting supports it correctly
    public def getResultSummary(key, value) {
        //TODO: raise exception if key or value is empty
        if (!isZafiraConnected()) {
            return
        }
        def parameters = [customHeaders     : [[name: 'Authorization', value: "${authToken}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'GET',
                          validResponseCodes: "200:401",
                          url               : this.serviceURL + "/api/reporting/v1/test-run-summaries?labelKey=${key}&labelValue=${value}"]

        return sendRequestFormatted(parameters)
    }

    protected boolean isTokenExpired() {
        return authToken == null || System.currentTimeMillis() > tokenExpTime
    }

    /** Verify if ZafiraConnected and refresh authToken if needed. Return false if connection can't be established or disabled **/
    protected boolean isZafiraConnected() {
        if (!isTokenExpired()) {
            logger.debug("Zebrunner Testing Platform connected")
            return true
        }

        if (isParamEmpty(this.refreshToken) || isParamEmpty(this.serviceURL) || Configuration.mustOverride.equals(this.serviceURL)) {
            logger.debug("Zebrunner Testing Platform is not connected!")
            logger.debug("refreshToken: ${this.refreshToken}; serviceURL: ${this.serviceURL};")
            return false
        }

        logger.debug("refreshToken: " + refreshToken)
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder refreshToken: this.refreshToken

        String requestBody = jsonBuilder.toString()
        jsonBuilder = null

        def parameters = [contentType       : 'APPLICATION_JSON',
                          httpMode          : 'POST',
                          validResponseCodes: "200:404",
                          requestBody       : requestBody,
                          url               : this.serviceURL + "/api/iam/v1/auth/refresh"]

        logger.debug("parameters: " + parameters)
        Map properties = (Map) sendRequestFormatted(parameters)
        logger.debug("properties: " + properties)
        if (isParamEmpty(properties)) {
            // #669: no sense to start tests if Zebrunner Testing Platform is configured and not available!
            logger.info("properties: " + properties)
            throw new RuntimeException("Unable to get auth token, check Zebrunner Testing Platform integration!")
        }
        this.authToken = properties.authTokenType + " " + properties.authToken
        logger.debug("authToken: " + authToken)
        this.tokenExpTime = System.currentTimeMillis() + 470 * 60 * 1000 //8 hours - interval '10 minutes'
        return true
    }

}

