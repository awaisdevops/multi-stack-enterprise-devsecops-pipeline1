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
                
        // versioning, gradle, git
        stage('App Version Bump') {
            steps {
                script {
                    sh 'chmod +x gradlew' // Ensure gradlew is executable
                    echo 'Incrementing app version in build.gradle...'
                    def buildFile = 'build.gradle'
                    def content = readFile(buildFile)
                    
                    def currentVersionString = extractVersion(content)
                    if (currentVersionString == null) {
                        error "Could not find a version in build.gradle"
                    }
                    
                    def versionParts = currentVersionString.replace("-SNAPSHOT", "").split('\\.')
                    
                    def major = versionParts[0] as int
                    def minor = versionParts[1] as int
                    def patch = versionParts[2] as int
                    
                    def newPatch = patch + 1
                    def newVersion = "$major.$minor.$newPatch"
                    def newVersionSnapshot = "$newVersion-SNAPSHOT"
                    
                    def newContent = content.replace("version = \"$currentVersionString\"", "version = \"$newVersionSnapshot\"")
                    writeFile(file: buildFile, text: newContent)
                    
                    echo "Version bumped from $currentVersionString to $newVersionSnapshot"
                    
                    env.IMAGE_NAME = "addsvcimg-$newVersion-$BUILD_NUMBER"
                }
            }
        }      
             
        // build, compile, package, gradle
        stage('Build & Package') {
            steps {
                script {
                    echo "building the application with Gradle..."
                    sh 'rm -rf ~/.gradle/caches'  // Clear Gradle cache
                    sh 'rm -rf build/'  // Clear local build directory
                    sh './gradlew clean --no-daemon installDist'
                }
            }
        }            

        // analysis, quality, sonarqube, security
        stage("SonarQube: Code Scan"){
            steps{                
                withSonarQubeEnv("SQ"){                    
                    sh "./gradlew sonar -Dsonar.projectKey=checkout-service -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.token=${SONAR_AUTH_TOKEN}"
                }
            }
        }   

        // test, quality, junit, gradle
        stage('Unit Tests') {
            steps {
                echo 'Running Unit Tests with Gradle...'
                sh './gradlew test'
            }            
            post {
                always {
                    // Collect and publish JUnit test reports from Gradle's default location
                    junit allowEmptyResults: true, testResults: '**/build/test-results/test/TEST-*.xml' 
                }
            }
        }

        // test, quality, integration, gradle
        stage('Integration Tests') {
            steps {
                echo 'Running Integration Tests with Gradle...'
                sh './gradlew integrationTest'
            }
            post {
                always {
                    // Collect and publish integration test reports
                    junit allowEmptyResults: true, testResults: '**/build/test-results/integrationTest/TEST-*.xml' 
                }
            }
        }
        
        /*
        // security, dependency, owasp
        stage("OWASP: Dependency Check"){
            steps{
                dependencyCheck additionalArguments: '--scan ./', odcInstallation: 'dc'
                dependencyCheckPublisher pattern: '/app-dep-check-report.html'
            }
        }   */ 
         
        
        // security, vulnerability, trivy, sast
        stage("Trivy: Filesystem Scan"){
            steps{
                sh "trivy fs --format  table -o trivy-fs-report.json ."
            }
        }    
       /*
        // quality, sonarqube, gate
        stage("SonarQube: Quality Gate"){
            steps{
                timeout(time: 10, unit: "MINUTES"){
                    waitForQualityGate abortPipeline: false
                }
            }
        }     
        */        
        
        // docker, build, container, image
        stage('Docker: Build Image') {              
            steps {
                script {
                    echo "building the docker image..."
                    withCredentials([usernamePassword(credentialsId: 'docker-hub-repo', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
                        sh "docker build -t ${DOCKER_REGISTRY}:${env.IMAGE_NAME} ."
                        sh 'echo $PASS | docker login -u $USER --password-stdin'
                        sh "docker push ${DOCKER_REGISTRY}:${env.IMAGE_NAME}"
                    }
                }
            }
        }   
        
        // security, vulnerability, trivy, docker
        stage('Trivy: Image Scan'){            
            steps{
                script {                    
                    def FULL_IMAGE_TAG = "${DOCKER_REGISTRY}:${env.IMAGE_NAME}"
                    sh "trivy image --format json -o trivy-image-report.json ${FULL_IMAGE_TAG}"                    
                    archiveArtifacts artifacts: 'trivy-image-report.json', onlyIfSuccessful: true
                }
            }
        }                                  
        
        
        // git, versioning, deployment, k8s
        stage('Update K8s Manifests in Main Branch') {
            steps {
                script {
                    // Capture the image name and registry before the shell block
                    def IMAGE_TAG = "${env.IMAGE_NAME}"
                    def REGISTRY = "${DOCKER_REGISTRY}"
                    
                    withCredentials([usernamePassword(credentialsId: 'github-credentials', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
                        sh '''
                            # Cleanup any existing infra-repo directory from previous runs
                            rm -rf infra-repo
                            
                            # Clone the main branch repo containing K8s manifests
                            git clone https://x-oauth-basic:${PASS}@github.com/awaisdevops/multi-stack-enterprise-devsecops-pipeline1.git infra-repo
                            cd infra-repo
                            
                            # Printing the current branch
                            git branch
                            
                            # Checking out to main branch
                            git checkout main
                            
                            # Configure Git
                            git config --global user.email "jenkins@example.com"
                            git config --global user.name "jenkins"
                            
                            # Update the image tag for adservice component in K8s deployments
                            # Pattern: Match Docker Build Image stage pattern - REGISTRY:IMAGE_TAG
                            echo "Updating adservice image to: ''' + REGISTRY + ''':''' + IMAGE_TAG + '''"
                            
                            # Checks for the 'k8s/deployments' directory and 'adservice-config.yaml' file,
                            # then uses 'sed' to update the Docker image tag for 'adservice' within the YAML file.
                            if [ -d "k8s/deployments" ]; then
                                if [ -f "k8s/deployments/adservice-config.yaml" ]; then
                                    sed -i "s|''' + REGISTRY + ''':adservice[^ ]*|''' + REGISTRY + ''':''' + IMAGE_TAG + '''|g" k8s/deployments/adservice-config.yaml
                                    echo "Updated: k8s/deployments/adservice-config.yaml"
                                fi
                            fi
                            
                            # Commit and push changes to main branch
                            git add k8s/
                            git commit -m "ci: Update adservice image to ''' + REGISTRY + ''':''' + IMAGE_TAG + ''' [skip ci]" || echo "No changes to commit"
                            git push origin main
                            
                            # Cleanup
                            cd ..
                            rm -rf infra-repo
                        '''
                    }
                }
            }
            
        }
        
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
                        git pull --rebase origin adservive
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