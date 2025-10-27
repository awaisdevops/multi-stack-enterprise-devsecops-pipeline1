@NonCPS
def extractVersion(text) {
    def matcher = text =~ /version = "(.+)"/
    return matcher.find() ? matcher.group(1) : null
}


pipeline {
    agent any
    
    tools {
        jdk 'jdk21' 
    }
        
    // Environment-specific configurations
    environment {
        // Docker registry
        DOCKER_REGISTRY = 'awaisakram11199/devopsimages'
        
        // AWS Region
        AWS_REGION = 'ap-northeast-2'

        // Sonar Qube
        SONAR_HOME= tool "SQ"
    }   

    
    stages {        
                
                                         
        
        // git, versioning, commit, scm
        stage('Commit App Version') {
            steps {
                script {

                    // Retrieve the credentials. $PASS MUST be the GitHub Personal Access Token (PAT).
                  
                  withCredentials([usernamePassword(credentialsId: 'github-credentials', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
                                  
                    // --- GITHUB PAT AUTH FIX ---
                    // GitHub rejects the traditional 'username:password@...' format.
                    // It requires the token to be used as the password with 'x-oauth-basic' as the placeholder username.
                    
                    def patUsername = "x-oauth-basic"
                    
                    // Construct the secure URL: https://x-oauth-basic:<PAT>@github.com/...
                    
                    def remoteUrl = "https://${patUsername}:${PASS}@github.com/awaisdevops/multi-stack-enterprise-devsecops-pipeline1.git"
                    
                    // ---------------------------

                    // 1. Configure Git for the commit author                    
                    sh 'git config --global user.email "jenkins@example.com"'
                    sh 'git config --global user.name "jenkins"'

                    // 2. Set the remote URL using the PAT-based authentication URL                    
                    sh 'git remote set-url origin ' + remoteUrl
                    
                    // 3. Commit and Push  
                    sh '''
                        git add build.gradle
                        git add src/
                        git commit -m "ci: Automated version bump [skip ci]"
                        git push origin HEAD:refs/heads/adservive
                    '''
                    }
                }
            }
        }
        
    }   
    
    post {
        // Send email on successful completion
        success {
            mail to: 'awais.akram11199@gmail.com',
                 subject: "SUCCESS addsvcimg: Jenkins Build ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
                 body: "The Jenkins build was successful. It successfully built the image with dynamically incremented application version and pushed it to the DockerHub. Check the build details here: ${env.BUILD_URL}"
        }

        // Send email on unstable completion (e.g., tests failed)
        unstable {
            mail to: 'awais.akram11199@gmail.com', // Add QA/Test Automation Engineer Email
                 subject: "UNSTABLE: Jenkins Build ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
                 body: "The Jenkins build is UNSTABLE (e.g., tests failed). Please review: ${env.BUILD_URL}"
        }

        // Send a different email on failure
        failure {
            mail to: 'awais.akram11199@gmail.com',
                 subject: "FAILURE: Jenkins Build ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
                 body: "The Jenkins build FAILED! Please investigate immediately: ${env.BUILD_URL}"
        }
    }
}