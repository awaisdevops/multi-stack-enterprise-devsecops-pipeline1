pipeline {
    agent any

    // Environment-specific configurations
    environment {
                
        // AWS Region
        AWS_REGION = 'ap-northeast-2'

        AWS_ACCESS_KEY_ID = credentials('jenkins_aws_access_key_id')
        AWS_SECRET_ACCESS_KEY = credentials('jenkins_aws_secret_access_key')
       
    }   
    
    stages {        
             
        stage("Terraform: Plan"){
           
            steps{
                script {
                    echo '==========================================='
                    echo 'Planning Infrastructure Changes...'
                    echo '==========================================='
                    
                    dir('infra') {
                        try {
                            sh 'aws sts get-caller-identity'
                            sh 'terraform init -upgrade'
                            sh 'terraform validate'
                            sh 'terraform plan -out=tfplan -input=false'
                            
                            archiveArtifacts artifacts: 'tfplan', allowEmptyArchive: false
                            
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
                        sh 'rm -f tfplan 2>/dev/null || true'
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