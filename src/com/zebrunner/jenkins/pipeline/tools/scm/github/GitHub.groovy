package com.zebrunner.jenkins.pipeline.tools.scm.github

import com.zebrunner.jenkins.pipeline.tools.scm.Scm
import com.zebrunner.jenkins.pipeline.Configuration

class GitHub extends Scm {

    GitHub(context) {
        super(context)
        
        this.prRefSpec = '+refs/pull/*:refs/remotes/origin/pr/*'
        // this.branchSpec = "origin/pr/%s/merge"
        // #70: above branchSpec can't get latest commit so reused sha1 for PR checker!
        this.branchSpec = "%s"
        
    }

    enum HookArgs {
        GIT_TYPE("scmType", "github"),
        SSH_URL("sshUrl", "\$.repository.ssh_url"),
        HTTP_URL("httpUrl", "\$.repository.clone_url"),
        HEADER_EVENT_NAME("eventName", "x-github-event"),

        PR_ACTION("prAction", "\$.action"),
        PR_SHA("prSha", "\$.pull_request.head.sha"),
        PR_NUMBER("prNumber", "\$.number"),
        PR_REPO("prRepo", "\$.pull_request.base.repo.full_name"),
        PR_SOURCE_BRANCH("prSourceBranch", "\$.pull_request.head.ref"),
        PR_TARGET_BRANCH("prTargetBranch", "\$.pull_request.base.ref"),
        PR_FILTER_TEXT("prFilterText", "\$pr_action \$x_github_event %s"),
        PR_FILTER_REGEX("prFilterExpression", "^((opened|reopened|synchronize)\\spull_request\\s%s)*?\$"),

        
        PUSH_FILTER_TEXT("pushFilterText", "\$ref \$x_github_event %s"),
        PUSH_FILTER_REGEX("pushFilterExpression", "^(\\Qrefs/heads/%s\\E\\spush\\s\\Q%s\\E)*?\$"),
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
        return String.format(branchSpec, Configuration.get('pr_sha'))
    }
    
    @Override
    public def webHookArgs() {
        return HookArgs.values().collectEntries {
            [(it.getKey()): it.getValue()]
        }
    }

}
