/*
Jenkinsfile for Admin Core application.

Pre-requisites:
GIT plugin
Pipeline plugin
Docker plugin
ECR publish plugin (https://wiki.jenkins.io/display/JENKINS/Amazon+ECR)
AWS Credentials having access for ECR publishing, and AWS SSM Parameter Store access.

Conventions:
1) AWS Credential ID should be in the form of:
aws-ecs-access-<environment-name> Environemnt can be one of staging, pre-production, production
Example: aws-ecs-access-staging

2) AWS ECR repository name should be in the form of:
<project-name>-<environment-name>
Example: <project-name>-staging
*/
@Library('jenkins-shared-lib') _
pipeline {
    agent any
    options {
        skipStagesAfterUnstable()
    }

    parameters {
        string(name: 'tag', defaultValue: "", description: 'Tag to build')
        choice(choices: ['dev', 'test', 'staging', 'uat', 'pre-production', 'production'], description: 'Deploy to which environment?', name: 'environment')
    }

    stages {

        stage('Set environment variables')
                {
                    steps {
                        script{
                            env.BUILD_CAUSE = currentBuild.getBuildCauses()[0].shortDescription.contains("push by") ? 'push' : 'manual'
                            if( env.BUILD_CAUSE == 'push' ) {
                                env.ENVIRONMENT_NAME = sh(script: 'basename ${GIT_BRANCH}', returnStdout: true).trim()
                                env.TAG_TO_BUILD = env.ENVIRONMENT_NAME
                            }
                            else {
                                env.ENVIRONMENT_NAME = "${params.environment}"
                                env.TAG_TO_BUILD = "${params.tag}"
                            }

                            env.aws_credential_id = "aws-ecs-access-"+ env.ENVIRONMENT_NAME
                            env.REGION = "us-west-2"
                            env.PROJECT_NAME = 'admin-core-backend'
                            env.SERVICE_PATH = "packages/admin-core"
                            env.ECS_SERVICE_NAME = "hrx-backend-service"
                            env.SSM_PARAM_PREFIX = "admin-core-backend"
                            def buildURL = env.BUILD_URL
                            env.BLUE_BUILD_URL  = buildURL.replace("job/${env.JOB_NAME}", "blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}")
                        }
                    }
                }

        stage('Checkout tag') {
            agent none
            when {
                expression { env.BUILD_CAUSE == 'manual' }
            }
            steps {

                checkoutTag()
            }
        }

        stage("Check if we need to build") {
            when {
                not { expression { hasChangesIn(env.SERVICE_PATH, env.ENVIRONMENT_NAME ) == true } }
            }
            steps {
                script{
                    echo("Tag to build: "+ env.TAG_TO_BUILD + " Branch to build: "+ env.BRANCH_TO_BUILD_DEFAULT)
                    if( env.BUILD_CAUSE != 'manual' ) {
                        currentBuild.result = 'ABORTED'
                        error("No changes for ${env.SERVICE_PATH}/*. Aborted.")
                    }
                }
            }
        }

        stage('Scanning with Sonar')
                {
                    when {
                        expression { env.ENVIRONMENT_NAME =~ /(dev)/ }
                    }
                    steps{
                        withCredentials(
                                [[
                                         $class: 'UsernamePasswordMultiBinding',
                                         credentialsId: 'sonar-credentials',  // ID of credentials in Jenkins
                                         usernameVariable: 'SONAR_LOGIN',
                                         passwordVariable: 'SONAR_PASSWORD'
                                 ]]) {

                            dir("${env.WORKSPACE}/${env.SERVICE_PATH}"){
                                sh "npm install"
                                sh "sonar-scanner -Dsonar.login=$SONAR_LOGIN -Dsonar.password=$SONAR_PASSWORD"
                                sh "rm -rf node_modules"
                            }
                        }
                    }
                }

        stage('Compare AWS SSM Parameters')
                {
                    steps{

                        compareSsmParam(
                                aws_credential_id: "${env.aws_credential_id}",
                                AWS_ACCOUNT_ID: "${AWS_ACCOUNT_ID}",
                                BUILD_NUMBER: "${env.BUILD_NUMBER}",
                                ENVIRONMENT_NAME: "${ENVIRONMENT_NAME}"
                        )
                    }
                }

        stage('Build') {
            steps {
                echo "The tag/branch is: ${env.TAG_TO_BUILD}"
                echo "The environment is: ${env.ENVIRONMENT_NAME}"

                withCredentials(
                        [[
                                 $class: 'AmazonWebServicesCredentialsBinding',
                                 accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                                 credentialsId: aws_credential_id,  // ID of credentials in Jenkins
                                 secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                         ]]) {

                    dir("${env.WORKSPACE}/${env.SERVICE_PATH}"){
                        script{
                            env.AWS_ACCOUNT_ID = sh(script: 'AWS_ACCESS_KEY_ID='+AWS_ACCESS_KEY_ID+' AWS_SECRET_ACCESS_KEY='+AWS_SECRET_ACCESS_KEY+' AWS_REGION=$REGION aws sts get-caller-identity | jq ".Account" -r', returnStdout: true).trim()
                            //configure registry
                            docker.withRegistry('https://'+AWS_ACCOUNT_ID+'.dkr.ecr.'+env.REGION+'.amazonaws.com', 'ecr:'+env.REGION+':' + aws_credential_id ) {
                                //build image
                                def dockerImage =   docker.build(env.PROJECT_NAME+"-"+env.ENVIRONMENT_NAME+":"+env.BUILD_NUMBER, " --build-arg ENVIRONMENT="+env.ENVIRONMENT_NAME+" --build-arg AWS_REGION="+env.REGION+" .")
                                //push image
                                dockerImage.push()
                            }
                        }
                    }
                }
            }
        }
        stage('Update SSM parameter with ECR image URI'){
            steps {

                updateSsmParam(
                        aws_credential_id: "${env.aws_credential_id}",
                        AWS_ACCOUNT_ID: "${AWS_ACCOUNT_ID}",
                        ENVIRONMENT_NAME: "${ENVIRONMENT_NAME}",
                        BUILD_NUMBER: "${env.BUILD_NUMBER}",
                        TAG_TO_BUILD: "${TAG_TO_BUILD}"
                )
            }
        }
        stage('Deploy') {
            steps {
                build "hrx-ecs-terraform-${ENVIRONMENT_NAME}"
                //Wait until the service becomes stable:
                withCredentials(
                        [[
                                 $class: 'AmazonWebServicesCredentialsBinding',
                                 accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                                 credentialsId: aws_credential_id,  // ID of credentials in Jenkins
                                 secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                         ]]) {
                    script{
                        def deploymentStatus = sh(returnStatus: true, script: "aws ecs wait services-stable \
                        --cluster hrx-cluster-${ENVIRONMENT_NAME} \
                        --services ${ECS_SERVICE_NAME}-${ENVIRONMENT_NAME} \
                        --region ${REGION}")
                        env.rollBackDeployment=rollBackDeployment(
                                aws_credential_id: "${env.aws_credential_id}",
                                AWS_ACCOUNT_ID: "${AWS_ACCOUNT_ID}",
                                ENVIRONMENT_NAME: "${ENVIRONMENT_NAME}",
                                BUILD_NUMBER: "${env.BUILD_NUMBER}",
                                TAG_TO_BUILD: "${TAG_TO_BUILD}",
                        )
                        if( deploymentStatus != 0 ) {
                            currentBuild.result = 'FAILED'
                            if(rollBackDeployment)
                            {//Apply roll back to infrastructure if deployment params were updated.
                                build "hrx-ecs-terraform-${ENVIRONMENT_NAME}"
                                echo "Automatic Rollback initiated."
                            }
                            error("Error in deploying service.")
                        }
                    }
                }
            }
        }

        stage('Run Tests') {
            when {
                expression { env.ENVIRONMENT_NAME =~  /(staging)/ }
            }
            parallel {

                stage('Initiate test environment build') {
                    agent {
                        label "master"
                    }
                    steps {
                        script {
                            build(job: env.JOB_NAME, parameters: [string(name: 'tag', value: env.TAG_TO_BUILD ), string(name: 'environment', value:'test')], wait: false)
                        }
                    }
                }

                stage('Run Risk Management test') {
                    agent {
                        label "master"
                    }
                    steps {
                        script {
                            build "idm_backendTests./test_admin-core_riskMgmt"
                        }
                    }
                }
                stage('Run 401K Test') {
                    agent {
                        label "master"
                    }
                    steps {
                        script {
                            build "idm_backendTests./test_admin-core_401k"
                        }
                    }
                }
                stage('Run HR-Services Test') {
                    agent {
                        label "master"
                    }
                    steps {
                        script {
                            build "idm_backendTests./test_admin-core_hr-services"
                        }
                    }
                }
            }
        }
    }

    post{
        success {
            script {
                if( env.ENVIRONMENT_NAME =~ /(production)/ || env.ENVIRONMENT_NAME =~ /(pre-production)/ || env.ENVIRONMENT_NAME =~ /(uat)/ || env.ENVIRONMENT_NAME =~ /(dev)/ || env.ENVIRONMENT_NAME =~ /(staging)/){

                    sh """
              curl -H "Content-Type: application/json" -d '{"text": "Deployed ${env.PROJECT_NAME} ${env.TAG_TO_BUILD} to ${env.ENVIRONMENT_NAME} environment. (${env.BLUE_BUILD_URL})"}' https://outlook.office.com/webhook/4f009e6e-fd09-4c89-981e-405072023970@27d3059a-ce98-46a7-9665-afb1bb64a0d0/JenkinsCI/0183b18300804b5d8519fdc203369587/2558d65c-49e9-47de-bec5-0a651452d524
            """
                    echo ("Notification sent to Teams")
                }
            }
        }

        always {
            script {
                if( currentBuild.result != 'ABORTED')
                {
                    emailext body: "[${currentBuild.currentResult}] -  ${env.PROJECT_NAME}: Deploy ${env.TAG_TO_BUILD} to ${env.ENVIRONMENT_NAME} environment. (${env.BLUE_BUILD_URL})", recipientProviders: [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']], subject: "Jenkins Build ${BUILD_NUMBER} - ${env.PROJECT_NAME} - [${currentBuild.currentResult}]"
                }
            }
        }
    }
}

def boolean hasChangesIn(String module, String environment) {
    def branch = environment == 'production' ? 'master': environment

    if( branch != 'dev' && branch != 'staging')//DO not compare when branc/environment is dev or staging.
    {
        return true
    }

    def MASTER = sh(
            returnStdout: true,
            script: "git rev-parse origin/${branch}"
    ).trim()

    // Gets commit hash of HEAD commit. Jenkins will try to merge master into
    // HEAD before running checks. If this is a fast-forward merge, HEAD does
    // not change. If it is not a fast-forward merge, a new commit becomes HEAD
    // so we check for the non-master parent commit hash to get the original
    // HEAD. Jenkins does not save this hash in an environment variable.
    def HEAD = sh(
            returnStdout: true,
            script: "git show -s --no-abbrev-commit --pretty=format:%P%n%H%n HEAD | tr ' ' '\n' | grep -v ${MASTER} | head -n 1"
    ).trim()

    return sh(
            returnStatus: true,
            script: "git diff --name-only ${HEAD}...${MASTER} | grep ^${module}/"
    ) == 0
}

