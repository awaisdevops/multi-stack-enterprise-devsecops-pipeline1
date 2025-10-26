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
                    sh "./gradlew sonar -Dsonar.host.url=http://3.37.89.125:9000"
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
        }*/
        
        
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
        */

        // git, versioning, commit, scm
        stage('Commit App Version') {
            steps {
                script {
                  withCredentials([usernamePassword(credentialsId: 'github-credentials', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
                    sh '''
                        # Configure Git for the commit author
                        git config --global user.email "jenkins@example.com"
                        git config --global user.name "jenkins"
                        
                        # Set the remote URL using the PAT-based authentication
                        git remote set-url origin "https://x-oauth-basic:${PASS}@github.com/awaisdevops/enterprise-devsecops-java-pipeline1.git"
                        
                        # Fetch latest remote changes
                        git fetch origin
                        
                        # Checkout main branch and reset to origin/main to ensure clean state
                        git checkout -B main origin/main || git checkout -b main origin/main
                        
                        # Stage and commit the version bump
                        git add build.gradle
                        git commit -m "ci: Automated version bump [skip ci]" || echo "No changes to commit"
                        
                        # Push the commits to main
                        git push origin main
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