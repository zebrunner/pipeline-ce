com.zebrunnercom.zebrunnercom.zebrunnerpackage com.zebrunner.jenkins.pipeline.runner.gradle

import com.zebrunner.jenkins.Logger
import com.zebrunner.jenkins.pipeline.runner.AbstractRunner

//[VD] do not remove this important import!
import com.zebrunner.jenkins.pipeline.Configuration

import static com.zebrunner.jenkins.Utils.*

public class Runner extends AbstractRunner {

    public Runner(context) {
        super(context)
        
        setDisplayNameTemplate('#${BUILD_NUMBER}|${branch}')
    }

    //Events
    public void onPush() {
        context.node("gradle") {
            logger.info("Runner->onPush")
            getScm().clonePush()
            compile("./gradlew clean")
            jenkinsFileScan()
        }
    }

    public void onPullRequest() {
        context.node("gradle") {
            logger.info("Runner->onPullRequest")
            getScm().clonePR()
            compile("./gradlew clean", true)
        }
    }

    //Methods
    public void build() {
        context.node("gradle") {
            logger.info("Runner->build")
            scmClient.clone()
            context.stage("Gradle Build") {
                context.gradleBuild("./gradlew " + Configuration.get("gradle_tasks"))
            }
        }
    }
    
    protected void compile(goals, isPullRequest=false) {
        context.stage("Gradle Compile") {
            goals += getSonarGoals(isPullRequest)
            context.gradleBuild(goals)
        }
    }
    
    protected def getSonarGoals(isPullRequest=false) {
        def sonarGoals = sc.getGoals(isPullRequest)
        if (!isParamEmpty(sonarGoals)) {
            //added gradle specific goal
            sonarGoals += " sonarqube"
        }
        
        return sonarGoals
    }

}
