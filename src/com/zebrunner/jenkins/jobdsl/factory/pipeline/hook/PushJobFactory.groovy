package com.zebrunner.jenkins.jobdsl.factory.pipeline.hook

import com.zebrunner.jenkins.jobdsl.factory.pipeline.PipelineFactory
import com.zebrunner.jenkins.Logger

import groovy.transform.InheritConstructors

@InheritConstructors
public class PushJobFactory extends PipelineFactory {

    def host
    def organization
    def repo
    def branch
    def scmRepoUrl
    def userId
    def zafiraFields
    def isTestNgRunner
    def webHookArgs

    public PushJobFactory(folder, pipelineScript, jobName, desc, host, organization, repo, branch, scmRepoUrl, userId, isTestNgRunner, zafiraFields, webHookArgs) {
        this.folder = folder
        this.pipelineScript = pipelineScript
        this.name = jobName
        this.description = desc
        this.host = host
        this.organization = organization
        this.repo = repo
        this.branch = branch
        this.scmRepoUrl = scmRepoUrl
        this.userId = userId
        this.isTestNgRunner = isTestNgRunner
        this.zafiraFields = zafiraFields
        this.webHookArgs = webHookArgs
    }

    def create() {
        def pipelineJob = super.create()

        pipelineJob.with {

            //TODO: think about other parameters to support DevOps CI operations
            parameters {
                configure addHiddenParameter('GITHUB_HOST', '', host)
                configure addHiddenParameter('GITHUB_ORGANIZATION', '', organization)
                stringParam('repo', repo, 'GitHub repository for scanning')
                //TODO: analyze howto support several gc_GIT_BRACH basing on project
                stringParam('branch', this.branch, "SCM repository branch to run against")
                if (isTestNgRunner) {
                    booleanParam('onlyUpdated', true, 'If chosen, scan will be performed only in case of any change in *.xml suites.')
                }
                choiceParam('removedConfigFilesAction', ['IGNORE', 'DELETE'], '')
                choiceParam('removedJobAction', ['IGNORE', 'DELETE'], '')
                choiceParam('removedViewAction', ['IGNORE', 'DELETE'], '')
                configure addHiddenParameter('userId', 'Identifier of the user who triggered the process', userId)
                configure addHiddenParameter('zafiraFields', '', zafiraFields)
                configure addHiddenParameter('ref', '', '')
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
                           }

                           genericHeaderVariables {
                            genericHeaderVariable {
                             key(webHookArgs.eventName)
                             regexpFilter("")
                            }
                           }
                           
                           tokenCredentialId("${this.organization}-webhook-token")
                           printContributedVariables(isLogLevelActive(Logger.LogLevel.DEBUG))
                           printPostContent(isLogLevelActive(Logger.LogLevel.DEBUG))
                           silentResponse(false)
                           regexpFilterText(webHookArgs.pushFilterText)
                           regexpFilterExpression(webHookArgs.prFilterExpression)
                        }
                    }
                }
            }
        }
        return pipelineJob
    }

}
