package com.zebrunner.jenkins.pipeline.runner.gradle

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
            compile("clean")
            jenkinsFileScan()
        }
    }

    public void onPullRequest() {
        context.node("gradle") {
            logger.info("Runner->onPullRequest")
            getScm().clonePR()
            compile("clean", true)
        }
    }

    //Methods
    public void build() {
        context.node("gradle") {
            logger.info("Runner->build")
            scmClient.clone()
            context.stage("Gradle Build") {
                context.gradleBuild(Configuration.get("goals"))
            }
        }
    }
    
    protected void compile(goals, isPullRequest=false) {
        context.stage("Gradle Compile") {
            goals += sc.getGoals(isPullRequest)
            context.gradleBuild(goals)
        }
    }

}
