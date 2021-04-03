@Library('jenkins-pipeline-libs')
import lulu.jenkins.utils.*
import lulu.jenkins.ci.Constants

import java.util.Date
import java.text.SimpleDateFormat

String[] systemNS = ["dev","staging","ingress-nginx","monitoring","default","kube-external-secrets","kube-node-lease","kube-public","kube-system"]

node("ci"){

  timestamps(){

    String stageName = ""
    try{
        stage('Setup') {
            stageName = env.STAGE_NAME
            echo "### Cleanup workspace"
            deleteDir()
            echo "## Connect to cluster"
            K8sUtils.connectToGKECluster(this, "dev")
        }

        stage ("Delete old namespaces") {
            stageName = env.STAGE_NAME
            echo "### Delete namespaces"
            def deletedNS = []
            echo '## Get cluster namespaces'
            String nameSpacesList = sh (script: "kubectl get ns --show-labels | grep TTL | awk '{print \$1, \$4}'", returnStdout: true).trim()
            def nameSpaces = (nameSpacesList.size() > 0) ? nameSpacesList.split("\r?\n") : []
            for (String ns in nameSpaces){
                def (nsName, labels) = ns.split(" ")
                echo " ${nsName} labels: ${labels}"
                if (!(nsName in systemNS)){
                    boolean isDelete = false

                    String ttl = ""
                    String lastUpdate = ""
                    String doNotDelete  = "no"
                    for (l in labels.split(",")){
                        def (name, value) = l.split("=")
                        switch(name){
                            case "TTL":
                                ttl = value
                                break
                            case "last_update":
                                lastUpdate = value
                                break
                            case "do_not_delete":
                                doNotDelete = value
                                break
                        }
                    }
                    // validate labels
                    if (lastUpdate == "" || ttl == "" || doNotDelete == ""){
                        echo "Namespace ${nsName} has invalid labels: ${labels}!!!"
                        break
                    }

                    if (doNotDelete != "yes" ){
                        if (ttl == "0"){
                            isDelete = true
                        } else if (Utils.getRunDuration(this, lastUpdate) >= ttl.toInteger()){
                            isDelete = true
                        }
                        if (isDelete){
                            echo "Deleting ${nsName}"
                            sh "kubectl delete namespaces ${nsName}"
                            deletedNS.add (nsName)
                        }
                    }
                }
            }

            if (deletedNS.size() > 0){
                echo "## Keep the list of deleted namespaces"
                for (dn in deletedNS){
                    sh "echo ${dn} >> ${WORKSPACE}/deleted_namespaces.txt"
                }
                archiveArtifacts artifacts: "deleted_namespaces.txt"
            } else {
              echo "Nothing to delete"
            }

        }

        } catch (Exception e) {
            currentBuild.result = "FAILURE"
            stage("Notify") {
                echo "### Notify"
                def notify = new Notify(this)
                notify.slackNotify("#infrastructure_alerts", "slack_jenkins_ci", "", "", "", false, "Failed to delete namespaces!", false, stageName)
            }
            throw e
        }
    }

}
