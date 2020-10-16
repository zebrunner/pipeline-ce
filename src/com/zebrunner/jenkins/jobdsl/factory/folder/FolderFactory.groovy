package com.zebrunner.jenkins.jobdsl.factory.folder

import com.zebrunner.jenkins.jobdsl.factory.DslFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class FolderFactory extends DslFactory {

    public FolderFactory(name, description) {
        super(null, name, description)
    }

    public FolderFactory(folder, name, description) {
        super(folder, name, description)
    }

    def create() {
        //TODO: add support for multi-level sub-folders
        return _dslFactory.folder(getFullName())
    }

}