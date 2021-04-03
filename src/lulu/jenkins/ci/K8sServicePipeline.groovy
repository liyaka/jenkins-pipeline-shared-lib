package lulu.jenkins.ci

import lulu.jenkins.utils.*

class K8sServicePipeline extends BasePipeline {

  String dockerContext = "."
  String dockerImageName = ""
  String dockerImageFullName = ""
  String dockerRegistry = Constants.DEV_DOCKER_REGISTRY
  def additionalImages = []
  String deployTTL = ("${script.env.DEPLOY_TTL}" != "null") ? script.env.DEPLOY_TTL : "0"
  // Namespace to deploy from master
  String deployFromMaster = "dev"
  String preBuildCommand = ""

  K8sServicePipeline(script) {
      super(script)
  }

  @Override
  void runImpl() {
      script.stage('Setup', this.&setup)
      slackChannel = ( slackChannel == "") ? "jenkins_" + dockerImageName.replaceAll("-","_") : slackChannel
      script.stage('Checkout', this.&checkout)
      script.stage('BuildInfo', this.&buildInfo)
      script.stage('Build', this.&build)
      script.stage('Push Artifacts', this.&pushToRegistry)
  }

  @Override
  void postBuild(){
    // for master deploy to dev after successful build
    // for feature branches, only if IS_DEPLOY is on
    if ( ("${script.env.IS_DEPLOY}" != "null" && java.lang.Boolean.valueOf(script.env.IS_DEPLOY) ||
           branchName == "master") && script.currentBuild.currentResult == "SUCCESS"){
          script.stage('Deploy', this.&deploy)
    }
  }

  @Override
  void build() {
    script.echo "### Build"
    stageName = script.env.STAGE_NAME

    if (preBuildCommand != ""){
      script.sh preBuildCommand
    }

    dockerImageFullName = Constants.DEV_DOCKER_REGISTRY + "${dockerImageName}:${versionName}"
    script.echo "### Build Docker image ${dockerImageFullName}"
    script.sh "docker build --build-arg GIT_COMMIT=\$(git log -1 --format=%H) -t ${dockerImageFullName} ${dockerContext}"
    additionalImages.each {
      script.dir("${it}"){
        String addDockerImageFullName = Constants.DEV_DOCKER_REGISTRY + "${it}:${versionName}"
        script.echo "### Build Docker image ${addDockerImageFullName}"
        script.sh "docker build --build-arg GIT_COMMIT=\$(git log -1 --format=%H) -t ${addDockerImageFullName} ."
      }
    }

    // Additional build description
    if (branchName == "master"){
      script.currentBuild.description = script.currentBuild.description + " Helm Version: <b>${versionName}</b>"
    } else if (!ifPR){
      script.currentBuild.description = script.currentBuild.description + " Helm Version: <b>${versionName}</b>, deploy: ${script.env.IS_DEPLOY}, TTL: ${deployTTL}"
    }

    script.echo "### Verify Helm Chart"
    K8sUtils.verifyHelmChart(script, dockerImageName, versionBase, versionName)

    archiveTestResults()
  }

  void pushToRegistry(){
    script.echo "### Push to Docker Registry"
    stageName = script.env.STAGE_NAME

    if (script.currentBuild.currentResult == "SUCCESS") {
      K8sUtils.pushToRegistry(script, dockerImageFullName, versionName, additionalImages)
    } else {
      script.echo "Build Status is not SUCCESS, do not push the artifacts"
    }
  }

  void deploy(){
      script.echo "### Deploy"
      stageName = script.env.STAGE_NAME
      String namespace = ( branchName == "master") ? deployFromMaster : K8sUtils.fixNamespace(script, branchName)
      K8sUtils.deployTo(script, namespace, dockerImageName, createTag, deployTTL)
  }

}
