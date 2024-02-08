pipeline {
    agent {
        label 'memphis-jenkins-big-fleet,'
    }

    stages {
        stage('Build') {
            agent {
                    docker { image 'maven:3.8.4-openjdk-11' } // Use the Docker image as the agent for this stage
                        }            
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
