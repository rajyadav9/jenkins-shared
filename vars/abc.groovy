package net.my.jenkins.workflow
import com.cloudbees.groovy.cps.NonCPS
def call(Map stageParams)
{
    aws = "${env.PROJECT_NAME}"+ "raj" +"${stageParams.REGION}"

    sh"""
echo "abcccccc"
echo "${env.PROJECT_NAME}" 
echo "${stageParams.REGION}"
echo "${env.aaa}"
"""
}
//def xyz(){
//    echo "my name is raj hahah"
//    String ab = "aaaaa"
//    bcc = "abcabcabcabc"
//}