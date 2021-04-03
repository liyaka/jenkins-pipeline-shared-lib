package lulu.jenkins.utils;

import org.apache.commons.lang3.StringUtils
import lulu.jenkins.ci.Constants
import java.util.Date
import java.text.SimpleDateFormat

class K8sUtils implements Serializable {

  static String fixNamespace(script, String namespace){
    String namespaceName = namespace.replaceAll("_|/","-").replaceAll("[^a-zA-Z0-9]","-").toLowerCase()
    // the namespace name length should not be longer than 25 chars, no special chars in the end
    if (namespaceName.size() > 25){
      namespaceName = namespaceName[0..25]
    }
    if (namespaceName.reverse().take(1) == "-"){
      namespaceName = namespaceName[0..-2]
    }
    return namespaceName
  }

  static void connectToGKECluster(script, String namespace){
    String clusterName = ( "${namespace}" == "prod" ) ? Constants.K8S_CLUSTER_NAME_PROD: Constants.K8S_CLUSTER_NAME_DEV
    String googleProject = ( "${namespace}" == "prod" ) ? Constants.K8S_GOOGLE_PROJECT_PROD: Constants.K8S_GOOGLE_PROJECT_DEV
    String zone = ( "${namespace}" == "prod") ? Constants.K8S_ZONE_PROD: Constants.K8S_ZONE_DEV
    script.sh "gcloud container clusters get-credentials ${clusterName} --zone ${zone} --project ${googleProject}"
  }

  // TODO connectToEKSCluster

  static void verifyHelmChart(script, String chartName, String versionBase, String versionName){
    script.dir("helm_chart"){
      def listValuesFiles = script.sh (returnStdout: true, script: "ls ${chartName}/values*.yaml").trim()
      script.echo "## Verify that chart is well-formated for each values file"
      for (String vf : listValuesFiles.split("\\r?\\n")){
        script.echo "# ${vf}"
        script.sh "helm lint -f ${vf} ${chartName}"
      }
    }
  }

  static void buildHelmChart(script, String chartName, String versionBase, String versionName){
    script.dir("helm_chart"){
      verifyHelmChart(script, chartName, versionBase, versionName)

      script.echo "## Create helm package"
      script.sh "helm package --app-version ${versionBase} --version ${versionName} ${chartName}"
    }
  }

  static void pushToRegistry(script, String dockerImageFullName, String versionName, def additionalImages = []){
    script.sh "docker push ${dockerImageFullName}"
    additionalImages.each {
      String addDockerImageFullName = Constants.IR_DEV_DOCKER_REGISTRY + "${it}:${versionName}"
      script.echo "### Push Docker image ${addDockerImageFullName}"
      script.sh "docker push ${addDockerImageFullName}"
    }
  }

  static void deployTo(script, String namespace, String dockerImageName, String versionName, String deployTTL){

      String deployJobName = "deploy_" + dockerImageName.replaceAll("-","_") + "_k8s"
      script.echo "Deploy to ${namespace}, jobName: ${deployJobName}, versionName is ${versionName}"

      script.build job: deployJobName,
            parameters: [[$class: 'StringParameterValue', name:'NAMESPACE', value: namespace],
                         [$class: 'StringParameterValue', name:'TAG_NAME', value: versionName],
                         [$class: 'StringParameterValue', name:'DEPLOY_TTL', value: deployTTL],]
  }

  static void deployToProduction(script, String namespace, String dockerImageName, String versionName, boolean multiVersions = false ){

      String deployJobName = "deploy_" + dockerImageName.replaceAll("-","_") + "_k8s_PRODUCTION"
      script.echo "Deploy to ${namespace}, jobName: ${deployJobName}, versionName is ${versionName}"

      script.build job: deployJobName,
            parameters: [[$class: 'StringParameterValue', name:'NAMESPACE', value: namespace],
                         [$class: 'StringParameterValue', name:'TAG_NAME', value: versionName],
                         [$class: 'BooleanParameterValue', name:'MULTI_VERSIONS', value: multiVersions ]]
  }

  static void copySecrets(script, String environment, String copyToNamespace){
    script.echo "## Coping secrets to ${copyToNamespace} namespace"
    def listExtSecrets = script.sh (returnStdout: true, script: "kubectl get es -n default | grep ${environment}- | awk '{print \$1}'").trim().split("\n")
    for (es in listExtSecrets){
      script.sh """
        kubectl get es ${es} --namespace=default --export -o yaml | kubectl apply -n ${copyToNamespace} -f -
      """
    }
  }

  static void createNamespace(script, String environment, String namespace, String deployTTL){
    int nsExists = script.sh (script: """kubectl get namespaces | grep -c -w "${namespace}" || true""", returnStdout: true).trim().toInteger()
    def now = new Date().format('dd-MM-yyyy')
    if (nsExists == 0){
      script.echo "## Create namespace ${namespace}, add TTL labels"
      script.sh """
        kubectl create namespace ${namespace}
        kubectl label namespaces ${namespace} last_update=${now} TTL=${deployTTL} do_not_delete=no --overwrite
        """
      copySecrets(script, environment, namespace)
    }
    else {
      script.echo "## Namespace ${namespace} already exists, adding current deployment time as namespace label"
      script.sh "kubectl label namespaces ${namespace} last_update=${now} TTL=${deployTTL} --overwrite"
      copySecrets(script, environment, namespace)
    }
  }

  static void deployService(script, String namespace, String chartName, String valuesFile, String versionName, String helmReleaseName){
    script.sh """
        helm upgrade --install --cleanup-on-fail -n ${namespace} -f ${chartName}/${valuesFile} --set image.tag=${versionName} ${helmReleaseName} ./${chartName}
      """
  }

}
