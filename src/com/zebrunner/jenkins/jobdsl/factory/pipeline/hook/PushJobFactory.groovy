package com.zebrunner.jenkins.jobdsl.factory.pipeline.hook

import com.zebrunner.jenkins.jobdsl.factory.pipeline.PipelineFactory
import com.zebrunner.jenkins.Logger

import groovy.transform.InheritConstructors

@InheritConstructors
public class PushJobFactory extends PipelineFactory {

    def organization
    def repoUrl
    def branch
    def userId
    def isTestNgRunner
    def webHookArgs

    public PushJobFactory(folder, pipelineScript, jobName, desc, organization, repoUrl, branch, userId, isTestNgRunner, webHookArgs) {
        this.folder = folder
        this.pipelineScript = pipelineScript
        this.name = jobName
        this.description = desc
        this.organization = organization
        this.repoUrl = repoUrl
        this.branch = branch
        this.userId = userId
        this.isTestNgRunner = isTestNgRunner
        this.webHookArgs = webHookArgs
    }

    def create() {
        def pipelineJob = super.create()

        pipelineJob.with {

            parameters {
                configure addHiddenParameter('repoUrl', 'repository url', repoUrl)
                stringParam('branch', this.branch, "SCM repository branch to run against (use 'refs/tags/1.0' to clone by tag)")
                if (isTestNgRunner) {
                    booleanParam('onlyUpdated', true, 'If chosen, scan will be performed only in case of any change in *.xml suites.')
                }
                choiceParam('removedConfigFilesAction', ['IGNORE', 'DELETE'], '')
                choiceParam('removedJobAction', ['IGNORE', 'DELETE'], '')
                choiceParam('removedViewAction', ['IGNORE', 'DELETE'], '')
                configure addHiddenParameter('userId', 'Identifier of the user who triggered the process', userId)
                configure addHiddenParameter('ref', '', '')
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
                             key("ref")
                             value(webHookArgs.refJsonPath)
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
                           if (this.organization != null && !this.organization.isEmpty()) {
                               webhookTokenCreds = "${this.organization}-${this.webHookArgs.scmType}-webhook-token"
                           }
                           
                           tokenCredentialId(webhookTokenCreds)
                           printContributedVariables(isLogLevelActive(Logger.LogLevel.DEBUG))
                           printPostContent(isLogLevelActive(Logger.LogLevel.DEBUG))
                           silentResponse(false)
                           regexpFilterText(String.format(webHookArgs.pushFilterText, resolveUrl(this.repoUrl)))
                           regexpFilterExpression("bitbucket".equals(webHookArgs.scmType) ? String.format(webHookArgs.pushFilterExpression, repoUrl.split("/")[3] + "/" + repoUrl.split("/")[4].replace(".git", "")) : String.format(webHookArgs.pushFilterExpression, this.branch, this.repoUrl))
                        }
                    }
                }
            }
        }
        return pipelineJob
    }


}
