package com.zebrunner.jenkins.pipeline.integration.github

import com.zebrunner.jenkins.pipeline.integration.HttpClient
import groovy.json.JsonBuilder

class GitHubClient extends HttpClient {
    private String serviceURL
    private String authToken

    /**
     * GitHubClient
     * @param context current context object.
     * @param serviceURL GitHub service URL e.g. 'https://api.github.com'.
     * @param authToken GitHub access token in format 'Bearer ghp_...'.
     */
    public GitHubClient(context, serviceURL, authToken) {
        super(context)
        this.serviceURL = serviceURL
        this.authToken = authToken
    }
    /**
     * commentSha
     * @param context A string label to differentiate this status from the status of other systems. By default 'build'.
     * @param repo owner/repo_name, e.g. 'octocat/Hello-World'. Required parameter.
     * @param sha1 full sha1 of commit. Required parameter.
     * @param state the state of the status. Can be one of: error, failure, pending, success. Required parameter.
     * @param desc a short description of the status. By default 'null'.
     * @param targetUrl the target URL to associate with this status. By default 'null'.
     * @url <a href="https://docs.github.com/en/rest/commits/statuses?apiVersion=2022-11-28#create-a-commit-status">GitHub Docs</a>
     */
    public def commentSha(context="build", repo, sha1, state, desc=null, targetUrl=null) {
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder context: context,
                state: state,
                description: desc,
                target_url: targetUrl
        logger.info("REQUEST: " + jsonBuilder.toPrettyString())
        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}", maskValue: true]],
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