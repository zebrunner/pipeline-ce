package com.zebrunner.jenkins.jobdsl.factory.pipeline.hook

import com.zebrunner.jenkins.jobdsl.factory.pipeline.PipelineFactory
import com.zebrunner.jenkins.Logger

import groovy.transform.InheritConstructors

@InheritConstructors
public class PullRequestJobFactory extends PipelineFactory {

    def host
    def organization
    def repo
    def branch
    def scmRepoUrl
    def webHookArgs

    public PullRequestJobFactory(folder, pipelineScript, jobName, desc, host, organization, repo, branch, scmRepoUrl, webHookArgs) {
        this.folder = folder
        this.pipelineScript = pipelineScript
        this.name = jobName
        this.description = desc
        this.host = host
        this.organization = organization
        this.repo = repo
        this.branch = branch
        this.scmRepoUrl = scmRepoUrl
        this.webHookArgs = webHookArgs
    }

    def create() {
        def pipelineJob = super.create()


        pipelineJob.with {

            parameters {
                stringParam('repo', repo, 'Your GitHub repository for scanning')
                configure addHiddenParameter('branch', '', branch)
                configure addHiddenParameter('GITHUB_HOST', '', host)
                configure addHiddenParameter('GITHUB_ORGANIZATION', '', organization)
                stringParam('pr_number', '', '')
                stringParam('pr_repository', '', '')
                stringParam('pr_source_branch', '', '')
                stringParam('pr_target_branch', '', '')
                stringParam('pr_action', '', '')
                stringParam('pr_sha')
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
                            }

                            genericHeaderVariables {
                                genericHeaderVariable {
                                    key(webHookArgs.eventName)
                                    regexpFilter("")
                                }
                            }

                            tokenCredentialId("${this.organization}-webhook-token")
                            printContributedVariables(this.logger.isLogLevelActive(Logger.LogLevel.DEBUG))
                            printPostContent(this.logger.isLogLevelActive(Logger.LogLevel.DEBUG))
                            silentResponse(false)
                            regexpFilterText(webHookArgs.prFilterText)
                            regexpFilterExpression(webHookArgs.prFilterExpression)
                        }
                    }
                }
            }

            return pipelineJob
        }
    }

    protected def getGitHubAuthId(project) {
        return "https://api.github.com : ${project}-token"
    }
}
