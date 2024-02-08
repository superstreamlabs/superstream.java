pipeline {

    agent {
        docker {
            label 'memphis-jenkins-big-fleet,'
            image 'maven:3.8.4-openjdk-11'
        }
    }    

    stages {
        stage('Build') {          
            steps {
                sh 'java -version'
                // Add your build commands here
            }
        }

        stage('Test') {
            steps {
                sh 'echo "Run tests here"'
                // Add your test commands here
            }
        }
    }
    post {
        always {
            cleanWs()
        }
    }    
}
