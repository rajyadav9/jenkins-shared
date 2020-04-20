package net.my.jenkins.workflow
import com.cloudbees.groovy.cps.NonCPS
def call(Map stageParams)
{
    sh"""
echo "abcccccc"
echo "${env.PROJECT_NAME}" 
echo "${stageParams.REGION}"
"""
}