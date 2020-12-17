import com.zebrunner.jenkins.Logger
import groovy.transform.Field

@Field final Logger logger = new Logger(this)
@Field final String GRADLE_TOOL = "G6"

def call(goals = 'clean build') {
	stage('Gradle Build') {
		logger.info("gradleBuild->call")
		def script = ""

		if(!fileExists('gradlew')) {
			script = tool name: "${GRADLE_TOOL}", type: 'hudson.plugins.gradle.GradleInstallation'
			script += '/bin/gradle ${goals}'
		} else {
			// test if this command is needed for gradlew builds
			//sh 'cp ./config/gradle.properties ./gradle.properties' and this param -P version=${version}
			script = "chmod a+x gradlew && ./gradlew ${goals}"
		}
		
		if (isUnix()) {
			withGradle() {sh script}
		} else {
			error("Running gradle tasks on Windows not supported!")
		}
	}
}
