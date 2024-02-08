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
                sh 'ls -la && pwd'

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
