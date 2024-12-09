pipeline {
    agent any
    environment {
        dockerImage = ''
        KUBECONFIG = "${WORKSPACE}/cluster1.yaml" // Updated kubeconfig file path
    }
    stages {
        stage('Clone Repository') {
            steps {
                git branch: 'main', url: 'https://github.com/akshitha171200/swe645-assignment3.git'
            }
        }
        stage('Build Project') {
            steps {
                script {
                    dir('swe645') {
                        // Run Maven build inside the 'swe645' directory
                        sh 'mvn clean package -DskipTests'
                    }
                }
            }
        }
        stage('Verify JAR file exists') {
            steps {
                script {
                    sh 'ls -l swe645/target/'
                }
            }
        }
        stage('Build Docker Image') {
            steps {
                script {
                    // Use the newly built JAR file in the Docker image
                    dockerImage = docker.build("atheretu/swe645:${env.BUILD_ID}", "-f swe645/Dockerfile .")
                }
            }
        }
        stage('Push Docker Image') {
            steps {
                script {
                    docker.withRegistry('https://index.docker.io/v1/', 'docker-credentials') {
                        dockerImage.push()
                        dockerImage.push('latest')
                    }
                }
            }
        }
        stage('Deploy to Kubernetes') {
            steps {
                script {
                    // Read token from Jenkins credentials
                    withCredentials([string(credentialsId: 'kubeconfig', variable: 'TOKEN')]) {
                        // Update token in kubeconfig file
                        sh """
                            sed -i 's|token:.*|token: ${TOKEN}|g' cluster1.yaml
                        """
                    }
                    
                    // Use the modified kubeconfig file
                    withEnv(["KUBECONFIG=${WORKSPACE}/cluster1.yaml"]) {
                        sh 'kubectl apply -f deployment.yaml'
                        sh 'kubectl apply -f service.yaml'
                        sh 'kubectl rollout restart deployment/surveyform-deployment'
                        sh 'kubectl rollout status deployment/surveyform-deployment'
                    }
                }
            }
        }
    }
    post {
        success {
            echo 'Pipeline executed successfully!'
        }
        failure {
            echo 'Pipeline failed!'
        }
    }
}
