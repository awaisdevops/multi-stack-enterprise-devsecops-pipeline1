pipeline {
    agent any

    // Environment-specific configurations
    environment {
                
        // AWS Region
        AWS_REGION = 'ap-northeast-2'

        // AWS Credentials
        AWS_ACCESS_KEY_ID = credentials('jenkins_aws_access_key_id')
        AWS_SECRET_ACCESS_KEY = credentials('jenkins_aws_secret_access_key')
        
        // Docker Registry Credentials (for pulling private images from DockerHub)
        // Set these in Jenkins credentials as 'docker_registry_credentials'
        DOCKER_CREDENTIALS = credentials('docker_registry_credentials')
        DOCKER_USERNAME = credentials('docker_username')
        DOCKER_PASSWORD = credentials('docker_password')
       
    }   
    
    stages {        
        
        stage("Infrastructure: Security scanning") {
            steps {
                script {
                    echo 'Scanning Infrastructure for Security Issues with Trivy...'

                    dir('infra') {
                        // catchError runs the scan. If vulnerabilities are found (non-zero exit code),
                        // it marks the build as UNSTABLE instead of failing, allowing the pipeline to continue.
                        catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                            // Using 'trivy config' for IaC scanning.
                            // The report is saved as a text file for readability.
                            sh "trivy config --format table -o terraform-code-security-scan.txt ."
                        }

                        echo "✓ Trivy scan complete. Report saved as terraform-code-security-scan.txt"
                        echo "If vulnerabilities were found, the build is marked as UNSTABLE."
                        
                        // Archive the report for viewing from the Jenkins build page.
                        archiveArtifacts artifacts: 'terraform-code-security-scan.txt', allowEmptyArchive: true
                    }
                }
            }
        }
             
        stage("Terraform: Plan"){
           
            steps{
                script {
                    echo 'Planning Infrastructure Changes...'
                    
                    dir('infra') {
                        try {
                            sh 'aws sts get-caller-identity'
                            sh 'terraform init -upgrade'
                            sh 'terraform validate'
                            sh 'terraform plan -out=tfplan -input=false'
                            sh 'terraform show -json tfplan > tfplan.json'
                            
                            archiveArtifacts artifacts: 'tfplan, tfplan.json', allowEmptyArchive: false
                            
                            echo '✓ Terraform plan completed successfully'
                            
                        } catch (Exception e) {
                            echo "✗ Terraform planning failed: ${e.message}"
                            currentBuild.result = 'FAILURE'
                            error("Planning stage failed: ${e.message}")
                        }
                    }
                }
            }
        }

        stage("InfraCost: Infra Cost Estimation") {
            environment {
                // Define the Infracost API key from Jenkins credentials.
                // You must create a 'Secret Text' credential with the ID 'infracost_api_key' in Jenkins.
                INFRACOST_API_KEY = credentials('infracost_api_key')
            }
            steps {
                script {

                    echo 'Estimating Infrastructure Costs with Infracost...'
                    
                    dir('infra') {
                        try {
                            // Run infracost to get a cost breakdown from the Terraform plan JSON file.
                            // The output is saved to a text file for archiving.
                            sh 'infracost breakdown --path . --plan tfplan.json --format table > infracost-report.txt'
                            
                            echo "✓ Infracost analysis complete. Report saved as infracost-report.txt"
                            
                            // Print the report to the console for immediate visibility.
                            echo "---------------- Infracost Report ----------------"
                            def report = readFile 'infracost-report.txt'
                            echo report
                            echo "----------------------------------------------------"

                            archiveArtifacts artifacts: 'infracost-report.txt', allowEmptyArchive: false
                            
                        } catch (Exception e) {
                            echo "✗ Infracost analysis failed: ${e.message}"
                            // We will just warn and not fail the build for cost estimation errors.
                            currentBuild.result = 'UNSTABLE'
                        }
                    }
                }
            }
        }

        stage("Infra: Approve"){
            steps{
                script {
                    echo 'Waiting for manual approval to apply infrastructure changes...'
                    timeout(time: 30, unit: 'MINUTES') {
                        input message: 'Review the Terraform plan. Approve to proceed with applying changes.', 
                              ok: 'Apply Infrastructure'
                    }
                    echo '✓ Deployment approved'
                }
            }
        }

        stage("Infra: Apply & Kubeconfig"){
            environment {
                AWS_ACCESS_KEY_ID = credentials('jenkins_aws_access_key_id')
                AWS_SECRET_ACCESS_KEY = credentials('jenkins_aws_secret_access_key')
            }
            steps{
                script {
                    echo '==========================================='
                    echo 'Applying Infrastructure Changes...'
                    echo '==========================================='
                    
                    dir('infra') {
                        try {
                            sh 'aws sts get-caller-identity'
                            sh 'terraform apply -auto-approve -input=false tfplan'
                            
                            def clusterName = sh(
                                script: 'terraform output -raw cluster_name', 
                                returnStdout: true
                            ).trim()
                            
                            env.EKS_CLUSTER_NAME = clusterName

                            def certificateArn = sh(
                                script: 'terraform output -raw certificate_arn',
                                returnStdout: true
                            ).trim()

                            env.CERTIFICATE_ARN = certificateArn
                            
                            echo 'Configuring kubectl for EKS cluster...'
                            sh """
                                aws eks update-kubeconfig --name ${clusterName} --region ${AWS_REGION}
                                kubectl cluster-info
                            """
                            
                            echo '✓ Infrastructure Provisioned Successfully!'
                            
                        } catch (Exception e) {
                            echo "✗ Terraform apply failed: ${e.message}"
                            currentBuild.result = 'FAILURE'
                            error("Apply stage failed: ${e.message}")
                        }
                    }
                }
            }
            post {
                cleanup {
                    dir('infra') {
                        sh 'rm -f tfplan tfplan.json infracost-report.txt 2>/dev/null || true'
                    }
                }
            }
        }  

        stage("Setup & Deploy Ingress") {
            steps {
                script {
                    echo '=========================================='
                    echo 'Setting up Registry & Deploying Ingress...'
                    echo '=========================================='
                    try {
                        // Step 1: Setup Docker Registry Secret
                        echo ''
                        echo '📦 Step 1: Setting up Docker Registry Secret...'
                        echo '-------------------------------------------'
                        sh """
                            set -e
                            
                            # Get Docker credentials from Jenkins secrets
                            DOCKER_USER=\$(echo ${DOCKER_CREDENTIALS} | jq -r '.docker_user // empty')
                            DOCKER_PASS=\$(echo ${DOCKER_CREDENTIALS} | jq -r '.docker_pass // empty')
                            
                            # Fallback to environment variables if available
                            DOCKER_USER=\${DOCKER_USER:-\${DOCKER_USERNAME}}
                            DOCKER_PASS=\${DOCKER_PASS:-\${DOCKER_PASSWORD}}
                            
                            if [ -z "\${DOCKER_USER}" ] || [ -z "\${DOCKER_PASS}" ]; then
                                echo "⚠️  Warning: Docker credentials not fully configured"
                                echo "Using default 'dockerhub-secret' - ensure it exists in cluster"
                            else
                                echo "✓ Creating Docker registry secret..."
                                
                                # Create or update the docker registry secret
                                kubectl create secret docker-registry dockerhub-secret \\
                                    --docker-server=https://index.docker.io/v1/ \\
                                    --docker-username=\${DOCKER_USER} \\
                                    --docker-password=\${DOCKER_PASS} \\
                                    --docker-email=docker@example.com \\
                                    --dry-run=client -o yaml | kubectl apply -f -
                                
                                echo "✓ Docker registry secret configured successfully"
                            fi
                        """
                        
                        // Step 2: Deploy Ingress
                        echo ''
                        echo '🌐 Step 2: Deploying Ingress...'
                        echo '-------------------------------------------'
                        sh """
                            set -e
                            
                            # Retrieve the ACM certificate ARN from Terraform outputs
                            CERTIFICATE_ARN=\$(cd infra && terraform output -raw certificate_arn)
                            
                            echo "Using ACM Certificate ARN: \${CERTIFICATE_ARN}"
                            echo "✓ Applying Kubernetes Ingress manifests with certificate..."
                            
                            # Use envsubst to substitute \${CERTIFICATE_ARN} with the actual ARN
                            export CERTIFICATE_ARN="\${CERTIFICATE_ARN}"
                            
                            # Create a temporary file with substituted values
                            envsubst < k8s/deployments/frontend-ingress.yaml > /tmp/frontend-ingress-applied.yaml
                            
                            # Apply the processed manifest
                            kubectl apply -f /tmp/frontend-ingress-applied.yaml
                            
                            # Clean up temporary file
                            rm -f /tmp/frontend-ingress-applied.yaml
                        """
                        
                        echo ''
                        echo '✓ Frontend accessible at: https://devops-portfolio.site'
                        
                    } catch (Exception e) {
                        echo "✗ Setup & Deploy failed: ${e.message}"
                        currentBuild.result = 'FAILURE'
                        error("Setup & Deploy Ingress failed: ${e.message}")
                    }
                }
            }
        }

        stage("App: Blue-Green Deploy") {
            when {
                // This stage runs for any service branch in the multi-branch pipeline
                // Automatically detects service name from JOB_NAME for all services
                expression {
                    def services = ['adservice', 'cartservice', 'checkoutservice', 'currencyservice', 'emailservice', 
                                   'frontend', 'paymentservice', 'productcatalogservice', 'recommendationservice', 
                                   'redis-cart', 'shippingservice']
                    return services.any { env.JOB_NAME?.contains(it) ?: false }
                }
            }
            steps {
                script {
                    try {
                        // Extract service name from JOB_NAME
                        // Handles all service branches: adservice, cartservice, etc.
                        def serviceName = env.JOB_NAME?.tokenize('-')[0] ?: 'unknown'
                        
                        echo '=========================================='
                        echo "🚀 Blue-Green Deployment: ${serviceName}"
                        echo '=========================================='
                        
                        // Use environment variable or fall back to BUILD_TAG
                        def dockerImage = env.DOCKER_IMAGE_TAG ?: env.BUILD_TAG
                        
                        echo "Service: ${serviceName}"
                        echo "Docker Image: ${dockerImage}"
                        echo "Namespace: default"
                        
                        sh """
                            set -e
                            cd k8s/deployments
                            
                            # Make scripts executable
                            chmod +x scripts/deploy-blue-green.sh
                            chmod +x scripts/switch-slot.sh
                            chmod +x scripts/init-blue-green.sh
                            
                            # Run blue-green deployment for the service
                            # Parameters: service-name, docker-image, namespace, timeout
                            ./scripts/deploy-blue-green.sh '${serviceName}' '${dockerImage}' default 300
                        """
                        
                        echo "✓ Blue-green deployment completed successfully"
                        
                    } catch (Exception e) {
                        echo "✗ Blue-green deployment failed: ${e.message}"
                        currentBuild.result = 'FAILURE'
                        error("Blue-green deployment failed: ${e.message}")
                    }
                }
            }
        }
                                              
    
    post {
        // Send email on successful completion
        success {
            mail to: 'awais.akram11199@gmail.com',
                 subject: "SUCCESS: Jenkins Build ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
                 body: "The Jenkins build was successful. Check the build details here: ${env.BUILD_URL}"
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
