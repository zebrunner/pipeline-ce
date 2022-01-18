package com.zebrunner.jenkins.pipeline

import com.zebrunner.jenkins.BaseObject
import com.zebrunner.jenkins.jobdsl.factory.folder.FolderFactory
import com.zebrunner.jenkins.jobdsl.factory.pipeline.RegisterRepositoryJobFactory
import com.zebrunner.jenkins.pipeline.integration.zebrunner.ZebrunnerUpdater
import com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty
import org.jenkinsci.plugins.matrixauth.inheritance.NonInheritingStrategy
import jenkins.security.ApiTokenProperty
import hudson.security.ProjectMatrixAuthorizationStrategy

import static com.zebrunner.jenkins.Utils.*
import static com.zebrunner.jenkins.pipeline.Executor.*

class Organization extends BaseObject {
    
    private static final String RUNNER_CLASS = "com.zebrunner.jenkins.pipeline.runner.maven.TestNG"

    protected ZebrunnerUpdater zebrunnerUpdater

    protected def folderName
    protected def reportingServiceUrl
    protected def reportingAccessToken
    protected def customPipeline


    public Organization(context) {
        super(context)
        this.zebrunnerUpdater = new ZebrunnerUpdater(context)
        this.folderName = Configuration.get("folderName")
        this.reportingServiceUrl = Configuration.get("reportingServiceUrl")
        this.reportingAccessToken = Configuration.get("reportingAccessToken")
        this.customPipeline = Configuration.get("customPipeline")
    }

    public def register() {
        logger.info("Organization->register")
        setDisplayNameTemplate('#${BUILD_NUMBER}|${folderName}')
        currentBuild.displayName = getDisplayName()
        context.node('master') {
            context.timestamps {
                generateCreds()
                generateCiItems()
                logger.info("securityEnabled: " + Configuration.get("securityEnabled"))
                if (Configuration.get("securityEnabled")?.toBoolean()) {
                    setSecurity()
                }
                clean()
            }
        }
    }

    public def delete() {
        logger.info("Organization->delete")
        context.node('master') {
            context.timestamps {
                def folder = Configuration.get("folderName")
                def userName = folder + "-user"
                removeCredentials("$folder-.*") //remove all organization/folder related credentials
                deleteUser(userName)
                deleteFolder(folder)
                clean()
            }
        }
    }

    protected def deleteFolder(folderName) {
        context.stage("Delete folder") {
            def folder = getJenkinsFolderByName(folderName)
            if (!isParamEmpty(folder)) {
                folder.delete()
            }
        }
    }

    protected def deleteUser(userName) {
        context.stage("Delete user") {
            def user = User.getById(userName, false)
            if (!isParamEmpty(user)) {
                deleteUserGlobalPermissions(userName)
                user.delete()
            }
        }
    }

    protected def generateCiItems() {
        def folder = this.folderName
        context.stage("Register Organization") {
            if (!isParamEmpty(folder)) {
                registerObject("project_folder", new FolderFactory(folder, ""))
            }

            registerObject("register_repository_job", new RegisterRepositoryJobFactory(folder, getRegisterRepositoryScript(), 'RegisterRepository', ''))

            factoryRunner.run(dslObjects)
        }
    }

    protected def setSecurity() {
        def folder = this.folderName
        logger.info("Organization->setSecurity")
        def userName = folder + "-user"
        boolean initialized = false
        def integrationParameters = [:]
        try {
            createJenkinsUser(userName)
            grantUserGlobalPermissions(userName)
            grantUserFolderPermissions(folder, userName)
            def token = generateAPIToken(userName)
            if (token == null) {
                throw new RuntimeException("Token generation failed or token for user ${userName} is already exists")
            }
            integrationParameters = generateIntegrationParemetersMap(userName, token.tokenValue, folder)
            initialized = true
        } catch (Exception e) {
            logger.error("Something went wrong during secure folder initialization: \n${e}")
        }
        zebrunnerUpdater.sendInitResult(integrationParameters, initialized)
    }

    protected def generateAPIToken(userName) {
        def token = null
        def tokenName = userName + '_token'
        def user = User.getById(userName, false)
        def apiTokenProperty = user.getAllProperties().find {
            it instanceof ApiTokenProperty
        }
        def existingToken = apiTokenProperty?.getTokenList()?.find {
            tokenName.equals(it.name)
        }
        if (isParamEmpty(existingToken)) {
            token = Jenkins.instance.getDescriptorByType(ApiTokenProperty.DescriptorImpl.class).doGenerateNewToken(user, tokenName).jsonObject.data
        }
        return token
    }

    protected def createJenkinsUser(userName) {
        def password = UUID.randomUUID().toString()
        return !isParamEmpty(User.getById(userName, false)) ? User.getById(userName, false) : Jenkins.instance.securityRealm.createAccount(userName, password)
    }

    protected def grantUserGlobalPermissions(userName) {
        def authStrategy = Jenkins.instance.getAuthorizationStrategy()
        authStrategy.add(hudson.model.Hudson.READ, userName)
    }

    protected def deleteUserGlobalPermissions(userName){
        def authStrategy = Jenkins.instance.getAuthorizationStrategy()
        logger.debug("authStrategy: " + authStrategy.dump())

        if(authStrategy instanceof ProjectMatrixAuthorizationStrategy){
            logger.info("ProjectMatrixAuthorizationStrategy detected as expected...")

            //getting all granted permissions
            def permissions = authStrategy.getGrantedPermissions()
            for (Set<String> permissionUsers:permissions.values()) {
                // remove any project-based permission for current user
                permissionUsers.remove(userName)
            }
        } else {
            logger.error("Project-based Matrix Authorization Strategy not in use!")
        }
    }

    protected def grantUserFolderPermissions(folderName, userName) {
        def folder = getJenkinsFolderByName(folderName)
        if (folder == null) {
            logger.error("No folder ${folderName} was detected.")
            return
        }
        def authProperty = folder.properties.find {
            it instanceof AuthorizationMatrixProperty
        }

        if (authProperty == null) {
            authProperty = new AuthorizationMatrixProperty()
            folder.properties.add(authProperty)
        }

        authProperty.setInheritanceStrategy(new NonInheritingStrategy())

        def permissionsArray = [com.cloudbees.plugins.credentials.CredentialsProvider.CREATE,
                                com.cloudbees.plugins.credentials.CredentialsProvider.DELETE,
                                com.cloudbees.plugins.credentials.CredentialsProvider.MANAGE_DOMAINS,
                                com.cloudbees.plugins.credentials.CredentialsProvider.UPDATE,
                                com.cloudbees.plugins.credentials.CredentialsProvider.VIEW,
                                com.synopsys.arc.jenkins.plugins.ownership.OwnershipPlugin.MANAGE_ITEMS_OWNERSHIP,
                                hudson.model.Item.BUILD,
                                hudson.model.Item.CANCEL,
                                hudson.model.Item.CONFIGURE,
                                hudson.model.Item.CREATE,
                                hudson.model.Item.DELETE,
                                hudson.model.Item.DISCOVER,
                                hudson.model.Item.EXTENDED_READ,
                                hudson.model.Item.READ,
                                hudson.model.Item.WORKSPACE,
                                com.cloudbees.hudson.plugins.folder.relocate.RelocationAction.RELOCATE,
                                hudson.model.Run.DELETE,
                                hudson.model.Run.UPDATE,
                                org.jenkinsci.plugins.workflow.cps.replay.ReplayAction.REPLAY,
                                hudson.model.View.CONFIGURE,
                                hudson.model.View.CREATE,
                                hudson.model.View.DELETE,
                                hudson.model.View.READ,
                                hudson.scm.SCM.TAG]
        permissionsArray.each {
            authProperty.add(it, userName)
        }
        folder.save()
    }

    protected def generateIntegrationParemetersMap(userName, tokenValue, folder) {
        def integrationParameters = [:]
        String jenkinsUrl = Configuration.get(Configuration.Parameter.JOB_URL).split("/job/")[0]
        integrationParameters.JENKINS_URL = jenkinsUrl
        integrationParameters.JENKINS_USER = userName
        integrationParameters.JENKINS_API_TOKEN_OR_PASSWORD = tokenValue
        integrationParameters.JENKINS_FOLDER = folder
        return integrationParameters
    }

    protected String getRegisterRepositoryScript() {
        return "${getPipelineLibrary()}\nimport com.zebrunner.jenkins.pipeline.Repository;\nnew Repository(this).register()"
    }

    protected def generateCreds() {
        if (!isParamEmpty(this.reportingServiceUrl) && !isParamEmpty(this.reportingAccessToken)) {
            registerReportingCredentials(this.folderName, this.reportingServiceUrl, this.reportingAccessToken)
        }

        if (customPipeline?.toBoolean()) {
            registerCustomPipelineCreds(this.folderName, customPipeline)
        }
    }

    public def registerHubCredentials() {
        context.stage("Register Hub Credentials") {
            def orgFolderName = Configuration.get("folderName")
            def provider = Configuration.get("provider")
            // Example: http://demo.qaprosoft.com/selenoid/wd/hub
            def url = Configuration.get("url")
            
            setDisplayNameTemplate('#${BUILD_NUMBER}|${folderName}|${provider}')
            currentBuild.displayName = getDisplayName()
            
            if (isParamEmpty(url)) {
                throw new RuntimeException("Required 'url' field is missing!")
            }
            
            def hubURLCredName = "${provider}_hub"
            if (!isParamEmpty(orgFolderName)) {
                hubURLCredName = "${orgFolderName}" + "-" + hubURLCredName
            }

            updateJenkinsCredentials(hubURLCredName, "${provider} URL", Configuration.Parameter.SELENIUM_URL.getKey(), url)
        }
    }
    
    public def registerUserCredentials() {
        context.stage("Register User Credentials") {
            def jenkinsUser = !isParamEmpty(Configuration.get("jenkinsUser")) ? Configuration.get("jenkinsUser") : getBuildUser(context.currentBuild)
            if (updateJenkinsCredentials("token_" + jenkinsUser, jenkinsUser + " SCM token", this.scmUser, this.scmToken)) {
                logger.info(jenkinsUser + " credentials were successfully registered.")
            } else {
                throw new RuntimeException("User credentials were not registered successfully!")
            }
        }
    }
    
    public def registerMavenSettings() {
        context.stage("Register Maven Settings") {
            def name = Configuration.get("Name")
            def id = Configuration.get("ID")
            
            def mavenCreds = Configuration.CREDS_MAVEN_SETTINGS
            if (!isParamEmpty(this.folderName)) {
                mavenCreds = "${this.folderName}-maven"
            }
            
            if (updateJenkinsCredentials(mavenCreds, "Maven settings file", name, id)) {
                logger.info(mavenCreds + " settings were successfully registered.")
            } else {
                throw new RuntimeException("Maven settings were not registered successfully!")
            }
        }
    }
    
    public def registerReportingCredentials() {
        context.stage("Register Reporting Credentials") {
            Organization.registerReportingCredentials(this.folderName, this.reportingServiceUrl, this.reportingAccessToken)
        }
    }

    public static void registerReportingCredentials(orgFolderName, reportingServiceUrl, reportingAccessToken) {
        if (isParamEmpty(reportingServiceUrl)) {
            throw new RuntimeException("Unable to register reporting credentials! Required field 'reportingServiceUrl' is missing!")
        }

        if (isParamEmpty(reportingAccessToken)) {
            throw new RuntimeException("Unable to register reporting credentials! Required field 'reportingAccessToken' is missing!")
        }
        
        def configs = addCustomConfigFile(orgFolderName, reportingServiceUrl, reportingAccessToken)
        context.println("configs: " + configs)
    }

    protected def registerCustomPipelineCreds(orgFolderName, token) {
        def customPipelineCreds = Configuration.CREDS_CUSTOM_PIPELINE

        if (!isParamEmpty(orgFolderName)) {
            customPipeline = orgFolderName + "-" + customPipelineCreds
        }

        updateJenkinsCredentials(customPipeline, "", Configuration.CREDS_CUSTOM_PIPELINE + "-token", token)
    }

}
