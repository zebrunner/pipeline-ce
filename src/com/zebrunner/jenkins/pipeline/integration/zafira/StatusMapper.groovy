package com.zebrunner.jenkins.pipeline.integration.zafira

class StatusMapper {

    enum ZafiraStatus {
        PASSED(1),
        FAILED(5),
        SKIPPED(3),
        ABORTED(3),
        QUEUED(3),
        IN_PROGRESS(3),

        final int value

        ZafiraStatus(int value) {
            this.value = value
        }
    }
}
