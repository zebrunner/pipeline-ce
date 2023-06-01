package com.zebrunner.jenkins.pipeline.integration.github

import com.zebrunner.jenkins.pipeline.integration.HttpClient
import groovy.json.JsonBuilder

class GitHubClient extends HttpClient {
    private String serviceURL
    private String authToken
    
    public GitHubClient(context, serviceURL, authToken) {
        super(context)
        this.serviceURL = serviceURL
        this.authToken = authToken
    }
    
    public def commentSha(context="build", repo, sha1, state, desc, targetUrl) {
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder context: context,
                state: state,
                description: desc,
                target_url: targetUrl
        logger.info("REQUEST: " + jsonBuilder.toPrettyString())
        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType  : 'APPLICATION_JSON',
                          httpMode     : 'POST',
                          requestBody  : "${jsonBuilder}",
                          url          : this.serviceURL + "/repos/${repo}/statuses/${sha1}",]
        return sendRequestFormatted(parameters)
    }
    
    public def getLatestTag(repo) {
        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType  : 'APPLICATION_JSON',
                          httpMode     : 'GET',
                          url          : this.serviceURL + "/repos/${repo}/releases/latest",]
        def response = sendRequestFormatted(parameters)
        return response.tag_name
        
    }
}