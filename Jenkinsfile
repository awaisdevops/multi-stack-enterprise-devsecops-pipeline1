pipeline {
    agent any

    stages {
        stage('Build & Tag Docker Image') {
            steps {
                script {
                    withDockerRegistry(credentialsId: 'docker-hub-repo', toolName: 'docker') {
                        sh "docker build -t awaisakram11199/devopsimages:recmndsvc ."
                    }
                }
            }
        }
        
        stage('Push Docker Image') {
            steps {
                script {
                    withDockerRegistry(credentialsId: 'docker-hub-repo', toolName: 'docker') {
                        sh "docker push awaisakram11199/devopsimages:recmndsvc "
                    }
                }
            }
        }
    }
}
