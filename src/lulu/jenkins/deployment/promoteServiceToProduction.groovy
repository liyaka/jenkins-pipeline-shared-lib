@Library('jenkins-pipeline-libs')
import lulu.jenkins.utils.*
import lulu.jenkins.ci.Constants

node ("ci") {

  timestamps(){

    String stageName = ""
    String serviceName = env.SERVICE_NAME
    String versionName = env.VERSION_NAME
    String namespace = env.NAMESPACE

    try {

        stage("Setup") {
          stageName = env.STAGE_NAME
          echo "## Setup"
          currentBuild.description = "Service: <b>${serviceName}</b>, Version: <b>${versionName}</b>"
        }

        stage("Promote service") {
          stageName = env.STAGE_NAME
          echo "## Promote"
          sh """
            docker pull ${Constants.DEV_DOCKER_REGISTRY}${serviceName}:${versionName}
            docker tag ${Constants.DEV_DOCKER_REGISTRY}${serviceName}:${versionName} ${Constants.PROD_DOCKER_REGISTRY}${serviceName}:${versionName}
            docker push ${Constants.PROD_DOCKER_REGISTRY}${serviceName}:${versionName}
          """
        }

        if ("${env.DEPLOY_TO_PRODUCTION}".toBoolean()){
          echo "namespace is ${namespace}, serviceName is ${serviceName}, versionName is ${versionName}"
          K8sUtils.deployToProduction(this, namespace, serviceName, versionName)
        }

      } catch (Exception e) {
          currentBuild.result = "FAILURE"
          throw e
      } finally {
        stage("Notify") {
            stageName = env.STAGE_NAME
            String slackChannel = "jenkins_" + serviceName.replaceAll("ir-","").replaceAll("-","_")
            echo "### Notify"
            def notify = new Notify(this)
            String successMessage = "Service *${serviceName}* version *${versionName}* has been promoted, ready to be deployed to production environment"
            String failMessage = "Service *${serviceName}* version *${versionName}* failed be promoted, see log for errors"
            String additionalSlackMessage = (currentBuild.currentResult == "SUCCESS") ? successMessage : failMessage
            notify.slackNotify("${slackChannel}", "slack_jenkins_ci", "" , "${versionName}", "", false, additionalSlackMessage, false, stageName)
        }
      }

  }
}
