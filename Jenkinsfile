pipeline {
    agent any

    tools {
        go 'go'
    }
     
    // Environment-specific configurations
    environment {
        // Docker registry
        DOCKER_REGISTRY = 'awaisakram11199/devopsimages'
        
        // AWS Region
        AWS_REGION = 'ap-northeast-2'

        // Sonar Qube
        SONAR_HOME = tool "SQ"
    }   
    
    stages {
        
        stage('App Version Bump') {
            steps {
                script {
                    echo 'Incrementing app version...'
                    sh '''
                        // Read current version from VERSION file
                        if [ -f VERSION ]; then
                            CURRENT_VERSION=$(cat VERSION)
                        else
                            CURRENT_VERSION="0.0.0"
                        fi
                        
                        // Parse and increment patch version
                        IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VERSION"
                        PATCH=$((PATCH + 1))
                        NEW_VERSION="$MAJOR.$MINOR.$PATCH"
                        
                        // Write new version
                        echo "$NEW_VERSION" > VERSION
                    '''
                    def version = readFile('VERSION').trim()
                    env.IMAGE_NAME = "${version}-${BUILD_NUMBER}"
                    echo "Version set to: ${env.IMAGE_NAME}"
                }
            }
        }      

             
        stage('Build & Package') {
            steps {
                script {
                    echo "Building the Go application..."
                    sh '''
                        go mod download
                        go mod tidy
                        go build -o app .
                    '''
                }
            }
        }                     
  
        stage("SonarQube: Code Scan"){
            steps{
                withSonarQubeEnv("SQ"){                    
                    sh """
                        ${SONAR_HOME}/bin/sonar-scanner \
                            -Dsonar.projectKey=go-app \
                            -Dsonar.sources=. \
                            -Dsonar.exclusions=**/*_test.go,**/vendor/** \
                            -Dsonar.host.url=http://13.209.42.38:9000
                    """
                }
            }
        }     

        stage('Unit Tests') {
            steps {
                echo 'Running Unit Tests...'
                sh 'go test -v -coverprofile=coverage.out -covermode=atomic ./...'
            }            
            post {
                always {
                    // Generate JUnit-style test report
                    sh 'go test -v -json ./... > test-report.json || true'
                    junit allowEmptyResults: true, testResults: 'test-report.json'
                }
            }
        }

        stage('Integration Tests') {
            steps {
                echo 'Running Integration Tests...'
                sh 'go test -v -tags=integration -timeout 10m ./...'
            }
            post {
                always {
                    echo 'Integration tests completed'
                }
            }
        }
        
                
        stage("OWASP: Dependency Check"){
            steps{
                dependencyCheck additionalArguments: '--scan ./ --format JSON --out . ', odcInstallation: 'dc'
                dependencyCheckPublisher pattern: 'dependency-check-report.json'
            }
        }    
        
                
        stage("Trivy: Filesystem Scan"){
            steps{
                sh "trivy fs --format json -o trivy-fs-report.json ."
            }
        }           
       
        stage("SonarQube: Quality Gate"){
            steps{
                timeout(time: 10, unit: "MINUTES"){
                    waitForQualityGate abortPipeline: false
                }
            }
        }
        
        stage('Docker: Build Image') {              

            steps {
                script {
                    echo "Building the Docker image..."
                    withCredentials([usernamePassword(credentialsId: 'docker-hub-repo', passwordVariable: 'PASS', usernameVariable: 'USER')]) {

                        sh "docker build -t ${DOCKER_REGISTRY}:${env.IMAGE_NAME} ."
                        sh 'echo $PASS | docker login -u $USER --password-stdin'
                        sh "docker push ${DOCKER_REGISTRY}:${env.IMAGE_NAME}"
                        
                    }
                }
            }
        }   
        
        stage('Trivy: Image Scan'){            
            steps{
                script {                    
                    def FULL_IMAGE_TAG = "${DOCKER_REGISTRY}:${env.IMAGE_NAME}"

                    sh "trivy image --format json -o trivy-image-report.json ${FULL_IMAGE_TAG}"                    

                    archiveArtifacts artifacts: 'trivy-image-report.json', onlyIfSuccessful: true
                }
            }
        }   
                
        

       
                           
        stage('Commit App Version') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: 'github-credentials', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
                                  
                        def patUsername = "x-oauth-basic"
                        def remoteUrl = "https://${patUsername}:${PASS}@github.com/awaisdevops/multi-stack-enterprise-devsecops-pipeline.git"
                        
                        sh 'git config --global user.email "jenkins@example.com"'
                        sh 'git config --global user.name "jenkins"'

                        sh "git remote set-url origin ${remoteUrl}"
                        
                        sh '''
                            git add VERSION
                            git add go.mod go.sum
                            git commit -m "ci: Automated version bump [skip ci]" || true
                            git push origin HEAD:checkoutservice
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
                 subject: "SUCCESS: Jenkins Build ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
                 body: "The Jenkins build was successful.\n\nBuild Details:\nJob: ${env.JOB_NAME}\nBuild: ${env.BUILD_NUMBER}\nImage: ${DOCKER_REGISTRY}:${env.IMAGE_NAME}\n\nView build: ${env.BUILD_URL}"
        }

        // Send email on unstable completion (e.g., tests failed)
        unstable {
            mail to: 'awais.akram11199@gmail.com',
                 subject: "UNSTABLE: Jenkins Build ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
                 body: "The Jenkins build is UNSTABLE (e.g., tests failed).\n\nBuild Details:\nJob: ${env.JOB_NAME}\nBuild: ${env.BUILD_NUMBER}\n\nPlease review: ${env.BUILD_URL}"
        }

        // Send a different email on failure
        failure {
            mail to: 'awais.akram11199@gmail.com',
                 subject: "FAILURE: Jenkins Build ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
                 body: "The Jenkins build FAILED!\n\nBuild Details:\nJob: ${env.JOB_NAME}\nBuild: ${env.BUILD_NUMBER}\n\nPlease investigate immediately: ${env.BUILD_URL}"
        }
    }
    
}

