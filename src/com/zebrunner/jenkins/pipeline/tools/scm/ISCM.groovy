package com.zebrunner.jenkins.pipeline.tools.scm

public interface ISCM {

    public def clone()

    public def clone(isShallow)

    public def clone(gitUrl, branch, subFolder)

    public def clonePR()
    
    public def clonePush()

}