package net.my.jenkins.workflow
import com.cloudbees.groovy.cps.NonCPS
def call(Map stageParams)
{
echo "${stageParams.aws}"
    echo "testing jbfdbdb"

}