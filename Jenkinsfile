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
                dir ('superstream'){
                    sh "sed -i 's|<version>[0-9]\\+\\.[0-9]\\+(-SNAPSHOT)</version>|<version>${env.versionTag}-SNAPSHOT</version>|' pom.xml"
                    sh "cat pom.xml"
                    //  withCredentials([file(credentialsId: 'maven-settings-file-superstream', variable: 'MAVEN_SETTINGS')]) {
                    //     // sh 'mvn -s $MAVEN_SETTINGS deploy'
                    //     sh 'mvn -B package --file pom.xml'
                    //    sh 'mvn -s $MAVEN_SETTINGS deploy'
                    // }
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
