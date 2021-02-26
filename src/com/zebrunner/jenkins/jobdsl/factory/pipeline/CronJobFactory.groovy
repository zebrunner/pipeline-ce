package com.zebrunner.jenkins.jobdsl.factory.pipeline

@Grab('org.testng:testng:6.8.8')

import org.testng.xml.XmlSuite
import groovy.transform.InheritConstructors

import static com.zebrunner.jenkins.Utils.*

@InheritConstructors
public class CronJobFactory extends PipelineFactory {

    def repoUrl
    def branch
    def suitePath
    def scheduling
    def orgRepoScheduling

    public CronJobFactory(folder, pipelineScript, cronJobName, repoUrl, branch, suitePath, jobDesc, orgRepoScheduling) {
        this.folder = folder
        this.pipelineScript = pipelineScript
        this.description = jobDesc
        this.name = cronJobName
        this.repoUrl = repoUrl
        this.branch = branch
        this.suitePath = suitePath
        this.orgRepoScheduling = orgRepoScheduling
    }

    def create() {
        logger.info("CronJobFactory->create")
        XmlSuite currentSuite = parseSuite(suitePath)
        def pipelineJob = super.create()

        pipelineJob.with {
            authenticationToken('ciStart')
            
            //** Properties & Parameters Area **//*
            if (scheduling != null && orgRepoScheduling) {
                triggers {
                    cron(parseSheduling(scheduling))
                }
            }
            parameters {
                extensibleChoiceParameterDefinition {
                    name('env')
                    choiceListProvider {
                        textareaChoiceListProvider {
                            choiceListText(getEnvironments(currentSuite))
                            defaultChoice(getDefaultChoiceValue(currentSuite))
                            addEditedValue(false)
                            whenToAdd('Triggered')
                        }
                    }
                    editable(true)
                    description('Environment to test against')
                }
                configure addHiddenParameter('repoUrl', 'repository url', repoUrl)
                configure addHiddenParameter('ci_parent_url', '', '')
                configure addHiddenParameter('ci_parent_build', '', '')

                configure stringParam('branch', this.branch, "SCM repository branch to run against")
                stringParam('email_list', '', 'List of Users to be emailed after the test. If empty then populate from jenkinsEmail suite property')
                configure addExtensibleChoice('BuildPriority', "gc_BUILD_PRIORITY", "Priority of execution. Lower number means higher priority", "5")
                configure addHiddenParameter('zafiraFields', '', '')
            }

        }
        return pipelineJob
    }

    def setScheduling(scheduling) {
        this.scheduling = scheduling
    }

}
