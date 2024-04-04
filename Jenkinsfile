pipeline {

    agent {
        docker {
            label 'memphis-jenkins-big-fleet,'
            image 'maven:3.8.4-openjdk-11'
        }
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
                sh "sed -i -r 's|<version>[0-9]+\\.[0-9]+(-SNAPSHOT)</version>|<version>${env.versionTag}-SNAPSHOT</version>|' pom.xml"
                withCredentials([file(credentialsId: 'maven-settings-file-superstream', variable: 'MAVEN_SETTINGS')]) {
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
