pipeline {

    agent {
        docker {
            label 'memphis-jenkins-big-fleet,'
            image 'maven:3.8.4-openjdk-17'
            args '-u root'
        }
    } 

    environment {
            HOME = '/tmp'
            GPG_PASSPHRASE = credentials('gpg-key-passphrase')
            SLACK_CHANNEL  = '#jenkins-events'
    }

    stages {
        stage('Build and Deploy') {
            steps {
                script {
                    sh 'git config --global --add safe.directory $(pwd)'
                    env.GIT_AUTHOR = sh(script: 'git log -1 --pretty=%an', returnStdout: true).trim()
                    env.COMMIT_MESSAGE = sh(script: 'git log -1 --pretty=%B', returnStdout: true).trim()
                    def triggerCause = currentBuild.getBuildCauses().find { it._class == 'hudson.model.Cause$UserIdCause' }
                    env.TRIGGERED_BY = triggerCause ? triggerCause.userId : 'Commit'
                }                
                script {
                    def branchName = env.BRANCH_NAME ?: ''
                    // Check if the branch is 'latest'
                    if (branchName == 'master') {
                        // Read version from version-beta.conf
                        def version = readFile('version-beta.conf').trim()
                        // Set the VERSION environment variable to the version from the file
                        env.versionTag = version
                        echo "Using version from version-beta.conf: ${env.versionTag}"
                    }
                    if (branchName == 'latest') {
                        def version = readFile('version.conf').trim()
                        env.versionTag = version
                        echo "Using version from version.conf: ${env.versionTag}"                        
                    }
                }
                withCredentials([file(credentialsId: 'gpg-key', variable: 'GPG_KEY')]) {
                                        //   gpg --batch --import $GPG_KEY
                    sh '''
                      echo '${env.GPG_PASSPHRASE}' | gpg --batch --yes --passphrase-fd 0 --import $GPG_KEY
                      echo "allow-loopback-pinentry" > /tmp/.gnupg/gpg-agent.conf
                      echo "D64C041FB68170463BE78AD7C4E3F1A8A5F0A659:6:" | gpg --import-ownertrust                      
                    '''
                }
                withCredentials([file(credentialsId: 'settings-xml-superstream', variable: 'MAVEN_SETTINGS')]) {
                    sh "mvn -B package --file pom.xml"
                    sh "mvn versions:set -DnewVersion=${env.versionTag}"
                    sh "mvn -s $MAVEN_SETTINGS deploy -DautoPublish=true"
                }
                
            }
        }

        stage('Checkout to version branch'){
            when {
                    expression { env.BRANCH_NAME == 'latest' }
                }        
            steps {
                sh """
                    curl -L https://github.com/cli/cli/releases/download/v2.40.0/gh_2.40.0_linux_amd64.tar.gz -o gh.tar.gz 
                    tar -xvf gh.tar.gz
                    mv gh_2.40.0_linux_amd64/bin/gh /usr/local/bin 
                    rm -rf gh_2.40.0_linux_amd64 gh.tar.gz
                """
                withCredentials([sshUserPrivateKey(keyFileVariable:'check',credentialsId: 'main-github')]) {
                sh """
                GIT_SSH_COMMAND='ssh -i $check -o StrictHostKeyChecking=no' git checkout -b $versionTag
                GIT_SSH_COMMAND='ssh -i $check -o StrictHostKeyChecking=no' git push --set-upstream origin $versionTag
                """
                }
                withCredentials([string(credentialsId: 'gh_token', variable: 'GH_TOKEN')]) {
                sh """
                gh release create $versionTag --generate-notes
                """
                }
            }
        } 

    }
    post {
        always {
            cleanWs()
        }
        success {
            sendSlackNotification('SUCCESS')
        }
        failure {
            sendSlackNotification('FAILURE')
        }
    }    
}

// SlackSend Function
def sendSlackNotification(String jobResult) {
    def jobUrl = env.BUILD_URL
    def messageDetail = env.COMMIT_MESSAGE ? "Commit/PR by ${env.GIT_AUTHOR}:\n${env.COMMIT_MESSAGE}" : "No commit message available."
    def projectName = env.JOB_NAME

    slackSend (
        channel: "${env.SLACK_CHANNEL}",
        color: jobResult == 'SUCCESS' ? 'good' : 'danger',
        message: """\
*:rocket: Jenkins Build Notification :rocket:*

*Project:* `${projectName}`
*Build Number:* `#${env.BUILD_NUMBER}`
*Status:* ${jobResult == 'SUCCESS' ? ':white_check_mark: *Success*' : ':x: *Failure*'}

:information_source: ${messageDetail}
Triggered by: ${env.TRIGGERED_BY}
:link: *Build URL:* <${jobUrl}|View Build Details>
"""
    )
}

