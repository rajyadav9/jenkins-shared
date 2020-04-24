package net.my.jenkins.workflow
import com.cloudbees.groovy.cps.NonCPS
def call(Map params){

    if(currentBuild.getPreviousBuildInProgress()!= null)
    {

        env.aa = currentBuild.getPreviousBuildInProgress().getRawBuild().actions.find{ it instanceof ParametersAction }?.parameters.find{it.name == 'tag'}?.value
        echo "${env.aa}"
        if(params.tag == env.aa)
        {
            milestone label: '', ordinal:  Integer.parseInt(env.BUILD_ID) - 1
            milestone label: '', ordinal:  Integer.parseInt(env.BUILD_ID)
        }
    }
