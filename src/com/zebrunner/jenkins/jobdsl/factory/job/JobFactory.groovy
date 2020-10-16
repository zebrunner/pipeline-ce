package com.zebrunner.jenkins.jobdsl.factory.job

import com.zebrunner.jenkins.jobdsl.factory.DslFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class JobFactory extends DslFactory {
    def logRotator = 30

    public JobFactory(folder, name, description) {
        super(folder, name, description)
    }

    public JobFactory(folder, name, description, logRotator) {
        super(folder, name, description)
        this.logRotator = logRotator
    }

    def create() {
        def job = _dslFactory.freeStyleJob(getFullName()) {
            description "${description}"
            logRotator { numToKeep logRotator }
        }
        return job
    }
}