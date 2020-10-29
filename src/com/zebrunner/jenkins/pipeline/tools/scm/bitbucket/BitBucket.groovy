package com.zebrunner.jenkins.pipeline.tools.scm.bitbucket

import com.zebrunner.jenkins.pipeline.tools.scm.Scm
import com.zebrunner.jenkins.pipeline.Configuration

class BitBucket extends Scm {

    BitBucket(context) {
        super(context)
        this.prRefSpec = ''
        this.branchSpec = "refs/remotes/origin/%s"
    }

    enum HookArgs {
        GIT_TYPE("scmType", "bitbucket"),
        HEADER_EVENT_NAME("eventName", "x-event-key"),

        PR_ACTION("prAction", ""),
        PR_SHA("prSha", "\$.repository.workspace.slug"),
        PR_NUMBER("prNumber", "\$.pullrequest.id"),
        PR_REPO("prRepo", "\$.pullrequest.source.repository.name"),
        PR_SOURCE_BRANCH("prSourceBranch", "\$.pullrequest.source.branch.name"),
        PR_TARGET_BRANCH("prTargetBranch", "\$.pullrequest.destination.branch.name"),
        PR_FILTER_REGEX("prFilterExpression", "^(pullrequest:(created|updated))*?\$"),
        PR_FILTER_TEXT("prFilterText", "\$x_event_key"),

        PUSH_FILTER_TEXT("pushFilterText", "\$ref \$x_event_key"),
        PUSH_FILTER_REGEX("pushFilterExpression", "^(master\\srepo:push)*?\$"),
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
    
    @Override
    public def webHookArgs() {
        return HookArgs.values().collectEntries {
            [(it.getKey()): it.getValue()]
        }
    }

}
