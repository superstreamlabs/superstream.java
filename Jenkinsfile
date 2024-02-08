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
                dir ('superstream'){
                     withCredentials([file(credentialsId: 'maven-settings-file-superstream', variable: 'MAVEN_SETTINGS')]) {
                        // sh 'mvn -s $MAVEN_SETTINGS deploy'
                        sh 'mvn -B package --file pom.xml'
                       sh 'mvn -s $MAVEN_SETTINGS deploy'
                    }
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
