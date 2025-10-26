pipeline {
    agent any

    stages {
        stage('Build and Tag Docker  Image') {
            steps {
                script {
                    dir('src') {

                    withDockerRegistry(credentialsId: 'docker-hub-repo', toolName: 'docker') {
                        sh "docker build -t awaisakram11199/devopsimages:cartservice01 ."
                    }
                        }
                }
            }
        }
        
        stage('Push Docker Image') {
            steps {
                script {
                    withDockerRegistry(credentialsId: 'docker-hub-repo', toolName: 'docker') {
                        sh "docker push awaisakram11199/devopsimages:cartservice01 "
                    }
                }
            }
        }
    }
}
