package com.zebrunner.jenkins.jobdsl.factory.pipeline.hook

import com.zebrunner.jenkins.jobdsl.factory.pipeline.PipelineFactory
import com.zebrunner.jenkins.Logger

import groovy.transform.InheritConstructors

@InheritConstructors
public class PullRequestJobFactory extends PipelineFactory {

    def organization
    def repoUrl
    def branch
    def webHookArgs

    public PullRequestJobFactory(folder, pipelineScript, jobName, desc, organization, repoUrl, branch, webHookArgs) {
        this.folder = folder
        this.pipelineScript = pipelineScript
        this.name = jobName
        this.description = desc
        this.organization = organization
        this.repoUrl = repoUrl
        this.branch = branch
        this.webHookArgs = webHookArgs
    }

    def create() {
        def pipelineJob = super.create()


        pipelineJob.with {

            parameters {
                configure addHiddenParameter('repoUrl', 'repository url', repoUrl)
                configure addHiddenParameter('branch', '', branch)
                configure addHiddenParameter('pr_number', '', '')
                configure addHiddenParameter('pr_repository', '', '')
                configure addHiddenParameter('pr_source_branch', '', '')
                configure addHiddenParameter('pr_target_branch', '', '')
                configure addHiddenParameter('pr_action', '', '')
                configure addHiddenParameter('pr_sha', '', '')
                configure addHiddenParameter('http_url', '', '')
                configure addHiddenParameter('ssh_url', '', '')
                configure addHiddenParameter('scmType', '', webHookArgs.scmType)
            }


            properties {
                pipelineTriggers {
                    triggers {
                        genericTrigger {
                            genericVariables {
                                genericVariable {
                                    key("pr_number")
                                    value(webHookArgs.prNumber)
                                }
                                genericVariable {
                                    key("pr_repository")
                                    value(webHookArgs.prRepo)
                                }
                                genericVariable {
                                    key("pr_source_branch")
                                    value(webHookArgs.prSourceBranch)
                                }
                                genericVariable {
                                    key("pr_target_branch")
                                    value(webHookArgs.prTargetBranch)
                                }
                                genericVariable {
                                    key("pr_action")
                                    value(webHookArgs.prAction)
                                }
                                genericVariable {
                                    key("pr_sha")
                                    value(webHookArgs.prSha)
                                }
                                genericVariable {
                                    key("ssh_url")
                                    value(webHookArgs.sshUrl)
                                }
                                genericVariable {
                                    key("http_url")
                                    value(webHookArgs.httpUrl)
                                }
                            }

                            genericHeaderVariables {
                                genericHeaderVariable {
                                    key(webHookArgs.eventName)
                                    regexpFilter("")
                                }
                            }

                            def webhookTokenCreds = "${this.webHookArgs.scmType}-webhook-token"
                            if (!isParamEmpty(this.organization)) {
                                webhookTokenCreds = "${this.organization}-${this.webHookArgs.scmType}-webhook-token"
                            }
                            tokenCredentialId(webhookTokenCreds)
                            
                            printContributedVariables(isLogLevelActive(Logger.LogLevel.DEBUG))
                            printPostContent(isLogLevelActive(Logger.LogLevel.DEBUG))
                            silentResponse(false)
                            regexpFilterText(String.format(webHookArgs.prFilterText, resolveUrl(this.repoUrl)))
                            regexpFilterExpression(String.format(webHookArgs.prFilterExpression, this.repoUrl))
                        }
                    }
                }
            }

            return pipelineJob
        }
    }

}
