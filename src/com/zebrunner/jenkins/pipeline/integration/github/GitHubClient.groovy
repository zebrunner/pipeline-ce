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
    
    public def commentSha(context, repo, sha1, state, desc, targetUrl) {
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder context: "build",
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
}