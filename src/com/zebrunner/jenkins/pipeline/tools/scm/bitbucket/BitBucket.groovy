package com.zebrunner.jenkins.pipeline.tools.scm.bitbucket

import com.zebrunner.jenkins.pipeline.tools.scm.Scm

class BitBucket extends Scm {

    BitBucket(context, host, org, repo, branch) {
        super(context, host, org, repo, branch)
        this.prRefSpec = '+refs/pull/*:refs/remotes/origin/pr/*'
        this.branchSpec = "%s"
    }

    BitBucket(context) {
        super(context)
    }

    enum HookArgs {
        HEADER_EVENT_NAME("eventName", "x-event-key"),

        PR_ACTION("prAction", ""),
        PR_SHA("prSha", ""),
        PR_NUMBER("prNumber", "\$.pullrequest.id"),
        PR_REPO("prRepo", "\$.pullrequest.destination.repository.name"),
        PR_SOURCE_BRANCH("prSourceBranch", "\$.pullrequest.source.branch.name"),
        PR_TARGET_BRANCH("prTargetBranch", "\$.pullrequest.destination.branch.name"),
        PR_FILTER_REGEX("prFilterExpression", "^(pullrequest:(created|updated))*?\$"),
        PR_FILTER_TEXT("prFilterText", "\$x_event_key"),

        PUSH_FILTER_TEXT("pushFilterText", "\$ref x-event-key"),
        PUSH_FILTER_REGEX("pushFilterRegex", "^(master\\srepo:push)*?\$"),
        REF_JSON_PATH("refJsonPath", "\$.push.changes[0].new.name")

        private final String key
        private final String value

        HookArgs(String key, String value) {
            this.key = key
            this.value = value
        }

        public String getKey() { return key }

        public String getValue() {return value }
    }

    @Override
    protected String branchSpec() {
        return String.format(branchSpec, Configuration.get('pr_source_branch'))
    }

}
