package lulu.jenkins.deployment

import lulu.jenkins.ci.*
import lulu.jenkins.utils.*

class DeployK8sServicePipeline extends BasePipeline {

    String chartRepo = ""
    String chartName = ""

    String cluster = script.env.CLUSTER
    String namespace = script.env.NAMESPACE
    String tagName = script.env.TAG_NAME
    String versionName = tagName
    boolean multiVersions = false
    String linkToServices = ""

    DeployK8sServicePipeline(script) {
        super(script)
        isGitRepo = false
    }

    @Override
    void runImpl() {
        script.stage('Setup', this.&setup)
        script.stage('Checkout', this.&checkout)
        if (cluster != "prod"){
          script.stage('Create Namespace', this.&createNamespace)
        }
        script.stage('Deploy', this.&deploy)
    }

    @Override
    void setup(){
        stageName = script.env.STAGE_NAME
        script.echo "## Cleaning workspace"
        Utils.cleanWorkspace(script)
        namespace = K8sUtils.fixNamespace(script,namespace)
        script.currentBuild.description = "Namespace: <b>${namespace}</b>, Version: <b>${versionName}</b>"
        K8sUtils.connectToGKECluster(script, namespace)
    }

    @Override
    void checkout(){
        stageName = script.env.STAGE_NAME
        script.echo "### Checkout"
        GitUtils.checkout(script, Constants.ORG_NAME, chartRepo, tagName , "")
    }

    void createNamespace(){
        stageName = script.env.STAGE_NAME
        script.echo "## Create Namespace"
        K8sUtils.createNamespace(script, cluster, namespace, script.env.DEPLOY_TTL)
    }

    @Override
    void deploy(){
        stageName = script.env.STAGE_NAME
        script.echo "### Deploy"

        String helmReleaseName = (multiVersions) ? "${chartName}-${chartBaseVersion}" : "${chartName}"
        // defines which values file to use for deployment, for private envs use 'dev'
        String envConfig = ""
        if (namespace in ["prod"]){
            envConfig = "prod"
        } else if (namespace in ["dev", "staging", "qa"]){
            envConfig = namespace
        } else {
            envConfig = "dev"
        }

        script.echo " envConfig is ${envConfig}"
        script.dir("helm_chart"){
          K8sUtils.deployService(script, namespace, chartName, "values-${envConfig}.yaml", versionName, chartName)
        }
        linkToServices = Utils.getLinesFromLog(script, "service url is").join("\n")
    }

    @Override
    void slackNotify(boolean startMessage=false){
        script.echo "### Notify"
        def notify = new Notify(script)
        String successMessage = "Service *${chartName}* has been deployed successfully to *${namespace}* environment.\n${linkToServices}"
        String failMessage = "Service *${chartName}* failed to deploy to *${namespace}* environment"
        String additionalSlackMessage = (script.currentBuild.currentResult == "SUCCESS") ? successMessage : failMessage
        String slackChannel = ( "${script.env.SLACK_CHANNEL}" == "null" || "${script.env.SLACK_CHANNEL}" == "") ? "jenkins_" + chartName.replace("ir-","").replaceAll("-","_") : script.env.SLACK_CHANNEL
        script.echo "slackChannel is ${slackChannel}"
        notify.slackNotify(slackChannel, "slack_jenkins_ci", "" , "${versionName}", "", false, additionalSlackMessage, false, stageName)
    }

}
