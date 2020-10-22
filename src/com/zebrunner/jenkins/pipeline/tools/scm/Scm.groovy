package com.zebrunner.jenkins.pipeline.tools.scm

import com.zebrunner.jenkins.Logger
import com.zebrunner.jenkins.pipeline.Configuration

import static com.zebrunner.jenkins.pipeline.Executor.*
import static com.zebrunner.jenkins.Utils.*


abstract class Scm implements ISCM {

	protected def context
	protected def logger

	protected def prRefSpec
	protected def branchSpec
	
    protected def repoUrl // https or ssh repository url
    
	protected def branch
	protected def credentialsId
    
    protected abstract String branchSpec()
    public abstract def webHookArgs()
    
	Scm(context) { 
		this.context = context
		this.logger = new Logger(context)

		this.repoUrl = Configuration.get("repoUrl")
		this.branch = Configuration.get("branch")
		this.credentialsId = Configuration.get("credentialsId")
	}

	public def clone() {
		clone(true)
	}

	public def clone(isShallow) {
		context.stage('Checkout Repository') {
			logger.info("Git->clone; shallow: ${isShallow}")
			def fork = !isParamEmpty(Configuration.get("fork")) ? Configuration.get("fork").toBoolean() : false
			def userId = Configuration.get("BUILD_USER_ID")
			def gitUrl = this.repoUrl

			logger.info("REPO_URL: ${this.repoUrl}")
			logger.info("CREDENTIALS_ID: ${this.credentialsId}")

			if (fork) {
				def tokenName = "token_$userId"
				def userCredentials = getCredentials(tokenName)
				if (userCredentials) {
					def userName = ""
					def userPassword = ""
					context.withCredentials([context.usernamePassword(credentialsId: tokenName, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
						throw new RuntimeException("Cloning via fork is unsupported now!")
						gitUrl = "https://${scmHost}/${context.env.USERNAME}/${repo}"
						this.credentialsId = tokenName
						userName = context.env.USERNAME
						userPassword = context.env.PASSWORD
					}
					logger.debug("tokenName: ${tokenName}; name: ${userName}; password: ${userPassword}")
				} else {
					throw new RuntimeException("Unable to run from fork repo as ${tokenName} token is not registered on CI!")
				}
			}

			Map scmVars = context.checkout getCheckoutParams(gitUrl, branch, null, isShallow, true, "+refs/heads/${branch}:refs/remotes/origin/${branch}", this.credentialsId)
			Configuration.set("scm_url", gitUrl)
			Configuration.set("scm_branch", branch)
			Configuration.set("scm_commit", scmVars.GIT_COMMIT)
		}
	}

	//TODO: try to remove below method or combine with above clone operation
	public def clone(gitUrl, branch, subFolder) {
		context.stage('Checkout Repository') {
			logger.info("Git->clone\nREPO_URL: ${gitUrl}\nbranch: ${branch}")
			context.checkout getCheckoutParams(gitUrl, branch, subFolder, true, false, "+refs/heads/${branch}:refs/remotes/origin/${branch}", this.credentialsId)
		}
	}

	public def clonePR() {
		context.stage('Checkout Repository') {
			def branch = Configuration.get("pr_source_branch")
			def prNumber = Configuration.get('pr_number')
			logger.info("Git->clonePR\nREPO_URL: ${this.repoUrl}\nbranch: ${branch}")
			context.checkout getCheckoutParams(this.repoUrl, branchSpec(), ".", true, false, prRefSpec, this.credentialsId)
		}
	}

	public def clonePush() {
		context.stage('Checkout Repository') {
			def branch = Configuration.get("branch")
			logger.info("Git->clonePush\nREPO_URL: ${this.repoUrl}\nbranch: ${branch}")
			context.checkout getCheckoutParams(this.repoUrl, branch, null, false, true, "+refs/heads/${branch}:refs/remotes/origin/${branch}", this.credentialsId)
		}
	}

	protected def getCheckoutParams(gitUrl, branch, subFolder, shallow, changelog, refspecValue, credentialsIdValue) {
		def checkoutParams = [scm      : [$class                           : 'GitSCM',
										  branches                         : [[name: branch]],
										  doGenerateSubmoduleConfigurations: false,
										  extensions                       : [[$class: 'CheckoutOption', timeout: 15],
																			  [$class: 'CloneOption', noTags: true, reference: '', shallow: shallow, timeout: 15]],
										  submoduleCfg                     : [],
										  userRemoteConfigs                : [[url: gitUrl, refspec: refspecValue, credentialsId: credentialsIdValue]]],
                                          changelog: changelog,
                                          poll     : false
                             ]
		if (subFolder != null) {
			def subfolderExtension = [[$class: 'RelativeTargetDirectory', relativeTargetDir: subFolder]]
			checkoutParams.get("scm")["extensions"] = subfolderExtension
		}
		return checkoutParams
	}

}