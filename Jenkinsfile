pipeline {

    agent {
        docker {
            label 'memphis-jenkins-big-fleet,'
            // image 'maven:3.8.4-openjdk-17'
            image 'jelastic/maven:3.9.5-openjdk-21'
        }
    } 

    environment {
            HOME = '/tmp'
    }

    stages {
        stage('Build and Deploy') {
            steps {
                script {
                    def branchName = env.BRANCH_NAME ?: ''
                    // Check if the branch is 'latest'
                    if (branchName == 'master') {
                        // Read version from version-beta.conf
                        def version = readFile('version-beta.conf').trim()
                        // Set the VERSION environment variable to the version from the file
                        env.versionTag = version
                        echo "Using version from version-beta.conf: ${env.versionTag}"
                    } else {
                        def version = readFile('version.conf').trim()
                        env.versionTag = version
                        echo "Using version from version.conf: ${env.versionTag}"                        
                    }
                }
                withCredentials([file(credentialsId: 'gpg-key', variable: 'GPG_KEY')]) {
                                        //   gpg --batch --import $GPG_KEY
                    sh '''
                       echo 12345679 | gpg --batch --yes --passphrase-fd 0 --import $GPG_KEY

                       echo "allow-loopback-pinentry" > /tmp/.gnupg/gpg-agent.conf
                       echo RELOADAGENT | gpg-connect-agent 
                       echo "D64C041FB68170463BE78AD7C4E3F1A8A5F0A659:6:" | gpg --import-ownertrust                      
                    '''
                }                                
                // sh "sed -i -r 's|<version>[0-9]+\\.[0-9]+(-SNAPSHOT)</version>|<version>${env.versionTag}-SNAPSHOT</version>|' pom.xml"
                withCredentials([file(credentialsId: 'settings-xml-superstream', variable: 'MAVEN_SETTINGS')]) {
                    sh 'mvn -B package --file pom.xml'
                    sh 'mvn -s $MAVEN_SETTINGS deploy'
                }
                
            }
        }

        stage('Checkout to version branch'){
            when {
                    expression { env.BRANCH_NAME == 'latest' }
                }        
            steps {
                sh """
                sudo dnf config-manager --add-repo https://cli.github.com/packages/rpm/gh-cli.repo -y
                sudo dnf install gh -y
                """
                withCredentials([sshUserPrivateKey(keyFileVariable:'check',credentialsId: 'main-github')]) {
                sh """
                GIT_SSH_COMMAND='ssh -i $check' git checkout -b $versionTag
                GIT_SSH_COMMAND='ssh -i $check' git push --set-upstream origin $versionTag
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
    }    
}
