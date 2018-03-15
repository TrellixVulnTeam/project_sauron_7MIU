def stack_name = params.Stack_Name
def repo = params.Project
def branch = params.Branch
def swarm_hostname
switch(params.Swarm) {
  case "dev":
    swarm_hostname = "swarm-dev.mldev.cloud"
    ssh_agent_id = "docker-dev-swarm"
    aws_id = "ml-jenkins-dev"
    break
  case "integration":
    swarm_hostname = "swarm-int.mldev.cloud"
    ssh_agent_id = "docker-dev-swarm"
    aws_id = "ml-jenkins-dev"
    break
  default:
    swarm_hostname = "swarm-prod.mldev.cloud"
    ssh_agent_id = "docker-prod-swarm"
    aws_id = "walkietalkie-prod"
    break
}
def artifactory_server = Artifactory.server 'Macmillan-Artifactory'
def artifactory_target = "Macmillan-Product-Builds"
def deploy_download_spec = """{
  "files": [
  {
    "pattern": "${artifactory_target}/ml_unified_pipeline/master/",
    "target": "./",
    "explode": "true"
  }
 ]
}"""
def repo_download_spec = """{
  "files": [
  {
    "pattern": "${artifactory_target}/${repo}/${branch}/",
    "target": "./"
  }
 ]
}"""

pipeline {
  agent { label 'python3' }

  environment {
        CONSUL_HTTP_ADDR = '172.28.17.4:8500'
    }

  stages {
    stage('Get Artifacts') {
      steps {
        script {
          sh "rm -fR *"
          artifactory_server.download(deploy_download_spec)
          artifactory_server.download(repo_download_spec)
          sh "mv ml_unified_pipeline/master/* ./"
          sh "mv ./${repo}/${branch}/.key ./${repo}/"
          sh "mv ./${repo}/${branch}/.images ./${repo}/"
          sh "mv ./${repo}/${branch}/* ./${repo}/"
        }
      }
    }
    stage('Install Dependencies') {
      steps {
        sh "chmod +x install_requirements.sh"
        sh "./install_requirements.sh"
      }
    }
    stage("Build .ENV file"){
      steps {
        sh "python3 env_builder/env_builder.py -t consul -k ${repo}/.key -d ./.env -p ${params.Swarm}/${repo}"
      }
    }
    stage("Deploying stack to Swarm") {
      steps {
        script {
          sh "cp ${repo}/docker-compose-swarm.yml ./docker-compose-swarm.yml"
          sh "cat ${repo}/.images >> ./.env"
          sh "rm /var/lib/jenkins/.ssh/known_hosts"
          withCredentials([[$class: 'UsernamePasswordMultiBinding',
            credentialsId: 'artifactory-jenkins-user',
            usernameVariable: 'USERNAME', 
            passwordVariable: 'PASSWORD']]) {
              env.ARTIFACTORY_USER="${USERNAME}"
              env.ARTIFACTORY_PASSWORD="${PASSWORD}"
            }
          sshagent (credentials: ["${ssh_agent_id}"]) {
            sh "python3 deploy/main.py artifactory ${stack_name} ${swarm_hostname} 5"
          }
        }
      }
    }
    stage("Creating ELBs Service URLs"){
      steps {
        script {
          withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
            credentialsId: "${aws_id}",
            accessKeyVariable: 'ACCESS_KEY', 
            secretKeyVariable: 'SECRET_KEY']]) {
              env.AWS_ACCESS_KEY_ID="${ACCESS_KEY}"
              env.AWS_SECRET_ACCESS_KEY="${SECRET_KEY}"
              env.AWS_DEFAULT_REGION="us-east-1"
              sh "python3 deploy/cf_main.py load ${stack_name} ${swarm_hostname}"
            }
        }
      }
    }
  }
}