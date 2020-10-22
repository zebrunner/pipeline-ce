package com.zebrunner.jenkins.pipeline.tools.scm.github

import com.zebrunner.jenkins.pipeline.tools.scm.Scm
import com.zebrunner.jenkins.pipeline.Configuration

class GitHub extends Scm {

    GitHub(context, host, org, repo, branch) {
        super(context, host, org, repo, branch)
        this.prRefSpec = '+refs/pull/*:refs/remotes/origin/pr/*'
        this.branchSpec = "origin/pr/%s/merge"
    }

    GitHub(context) {
        super(context)
    }

    enum HookArgs {
        HEADER_EVENT_NAME("eventName", "x-github-event"),

        PR_ACTION("prAction", "\$.action"),
        PR_SHA("prSha", "\$.pull_request.head.sha"),
        PR_NUMBER("prNumber", "\$.number"),
        PR_REPO("prRepo", "\$.pull_request.base.repo.full_name"),
        PR_SOURCE_BRANCH("prSourceBranch", "\$.pull_request.head.ref"),
        PR_TARGET_BRANCH("prTargetBranch", "\$.pull_request.base.ref"),
        PR_FILTER_REGEX("prFilterExpression", "^((opened|reopened)\\spull_request)*?\$"),
        PR_FILTER_TEXT("prFilterText", "\$pr_action \$x_github_event"),

        
        PUSH_FILTER_TEXT("pushFilterText", "\$ref \$x_github_event"),
        PUSH_FILTER_REGEX("pushFilterExpression", "^(refs/heads/master\\spush)*?\$"),
        REF_JSON_PATH("refJsonPath", "\$.ref")

        private final String key
        private final String value

        HookArgs(String key, String value) {
            this.key = key
            this.value = value
        }

        public String getKey() { return key }

        public String getValue() { return value }
    }

    @Override
    protected String branchSpec() {
        return String.format(branchSpec, Configuration.get('pr_number'))
    }

}
