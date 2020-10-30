package com.zebrunner.jenkins.pipeline.integration.zebrunner

import com.zebrunner.jenkins.Logger
import com.zebrunner.jenkins.pipeline.Configuration

class ZebrunnerUpdater {

    private def context
    private ZebrunnerClient zc
    private Logger logger

    public ZebrunnerUpdater(context) {
        this.context = context
        zc = new ZebrunnerClient(context)
        logger = new Logger(context)
    }

    public def sendInitResult(integrationParameters, initialized) {
        def tenancyName = Configuration.get("folderName")
        def accessToken = Configuration.get("accessToken")
        def callbackURL = Configuration.get("callbackUrl")
        return zc.sendInitResult(integrationParameters, tenancyName, accessToken, callbackURL, initialized)
    }
}
