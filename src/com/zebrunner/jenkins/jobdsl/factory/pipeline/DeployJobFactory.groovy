package com.zebrunner.jenkins.jobdsl.factory.pipeline

import groovy.transform.InheritConstructors

@InheritConstructors
class DeployJobFactory extends PipelineFactory {

	def repoUrl
	def scmCredentialsId = ''

	public DeployJobFactory(folder, pipelineScript, jobName, repoUrl) {
		this(folder, pipelineScript, jobName, repoUrl, '')
	}

	public DeployJobFactory(folder, pipelineScript, jobName, repoUrl, scmCredentialsId) {
		this.name = jobName
		this.folder = folder
		this.pipelineScript = pipelineScript
		this.repoUrl = repoUrl
		this.scmCredentialsId = scmCredentialsId
	}

	def create() {
		logger.info("DeployJobFactory->Create")

		def pipelineJob = super.create()

		pipelineJob.with {
			parameters {
				configure addExtensibleChoice('TARGET_ENVIRONMENT', 'gc_DEPLOY_ENV', '', 'stage')
				configure stringParam('RELEASE_VERSION', '', '')
				configure addHiddenParameter('repoUrl', 'repository url', repoUrl)
				configure addHiddenParameter('scmCredentialsId', '', scmCredentialsId)
			}
		}

		return pipelineJob
	}

}
