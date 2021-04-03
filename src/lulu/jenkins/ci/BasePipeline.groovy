package lulu.jenkins.ci

import lulu.jenkins.utils.*

abstract class BasePipeline implements Serializable {

    def script
    String orgName = Constants.ORG_NAME
    String projectName
    String mainBranchName = "master"
    String branchName
    String targetBranchName = ""
    String versionBase
    String versionName
    String artifactsList = ""
    String artifactsListExcludes = ""
    boolean ifNotify = true
    boolean ifPR = false
    boolean isGitRepo = true
    String slackChannel = ""
    String additionalSlackMessage = ""
    String additionalSuccessSlackMessage = ""
    String stageName = ""
    boolean ifArchiveTestResults = true
    String pathToUnitTestResults = "**/build/test-results/test/*.xml"

    BasePipeline(script) {
        this.script = script
    }

    void setBranchProperties() {
        script.echo "### Set Branch Properties"

        if ( "${script.env.BRANCH_NAME}" == "" || "${script.env.BRANCH_NAME}" == "null" || "${script.env.BRANCH_NAME}" == mainBranchName || "${script.env.BRANCH_NAME}" == "origin/${mainBranchName}"){
            branchName = mainBranchName
            targetBranchName = mainBranchName
        } else {
            if (branchName.startsWith("PR")) {
              branchName = "${script.env.CHANGE_BRANCH}".replaceAll("origin/", "")
              targetBranchName = "${script.env.CHANGE_TARGET}".replaceAll("origin/", "")
              ifPR = true
            } else {
              branchName = "${script.env.BRANCH_NAME}".replaceAll("origin/", "")
              targetBranchName = mainBranchName
            }
        }
        script.echo "Branch is ${branchName}, targetBranchName is ${targetBranchName}"
    }

    void run() {
        script.timestamps() {
          script.ansiColor('xterm'){
            try {
                script.echo "## Running with params: ${this.properties}"
                runImpl()
                if (script.currentBuild.currentResult == "SUCCESS" && !ifPR) {
                  script.stage("TagBuild") {
                      script.echo "### TagBuild"
                      stageName = script.env.STAGE_NAME
                      GitUtils.tagBuild(script, projectName, versionName)
                  }
                }
                if (script.currentBuild.currentResult == "SUCCESS"){
                  postBuild()
                }

            } catch (Exception e) {
                script.currentBuild.result = "FAILURE"
                throw e
            } finally {
                script.stage("Archive") {
                    script.echo "### Archive"
                    archiveTestResults()
                    archiveFiles()
                }

                script.stage("Notify") {
                    script.echo "### Notify"
                    setBuildStatusInGit()
                    slackNotify(false)
                }
                script.stage ('Final') {
                  script.echo "### Final"
                  cleanupResources()
                }
            }
          }
        }
    }

    // Example of runImpl function that needs to be iomplemented
    void runImpl() {
        script.stage('Setup', this.&setup)
        script.stage('Checkout', this.&checkout)
        script.stage('BuildInfo', this.&buildInfo)
        script.stage('Build', this.&build)
        script.stage('Unit Tests', this.&unitTests)
        script.stage('Integration Tests', this.&integrationTests)
        script.stage('System Tests', this.&systemTests)
        script.stage('Deploy', this.&deploy)

    }

    void setup() {
        script.echo "### Setup"
        stageName = script.env.STAGE_NAME
        initParams()
        Utils.cleanWorkspace(script)
        slackChannel = ( slackChannel == "") ? "jenkins_" + projectName.replaceAll("-","_") : slackChannel

    }

    void checkout() {
        script.echo "### Checkout"
        stageName = script.env.STAGE_NAME
        script.echo "# Checkout ${orgName}, ${projectName}, ${branchName}"
        GitUtils.checkout(script, orgName, projectName, branchName)
    }

    void initParams() {}

    void buildInfo() {
        script.echo "### BuildInfo"
        stageName = script.env.STAGE_NAME
        setVersion()
        populateBuildDisplayName()
        populateBuildDescription()
    }

    void setVersion() {

        versionBase = Utils.getVersionBaseFromVersionFile(script)

        script.echo "## versionBase is ${versionBase}, branchName is ${branchName}"
        if (branchName == mainBranchName) {
            versionName = "${versionBase}-${script.env.BUILD_NUMBER}"
        } else {
          // Fixing branch name to fit helm chart version name format
          String fixedBranchName = branchName.replaceAll('/', "-").replaceAll("_","-").toLowerCase()
          if (fixedBranchName.size() > 50){
            fixedBranchName = fixedBranchName[0..50]
          }
          if (ifPR) {
              versionName = "${versionBase}-${fixedBranchName}.pr${script.env.CHANGE_ID}-${script.env.BUILD_NUMBER}"
          } else {
              versionName = "${versionBase}-${fixedBranchName}-${script.env.BUILD_NUMBER}"
          }
        }
        script.echo "## Version: ${versionName}"
    }

    void populateBuildDisplayName(String displayName = "") {
        if (displayName == ""){
          script.currentBuild.displayName = versionName
        } else {
          script.currentBuild.displayName = displayName
        }
    }

    void populateBuildDescription(String buildDescription = "") {
      if (buildDescription == ""){
        if (branchName == mainBranchName) {
            script.currentBuild.description = ""
        } else {
            script.currentBuild.description = "Branch: <b>${branchName}</b>"
        }
      } else {
        script.currentBuild.description = buildDescription
      }
    }

    void setBuildStatusInGit() {
        if (isGitRepo){
          GitUtils.setBuildStsinGitHub(script, projectName)
        }
    }

    void build() {}

    void deploy() {}

    void systemTests() {}

    void unitTests() {}

    void integrationTests() {}

    void postBuild(){}

    void cleanupResources() {}

    void archiveTestResults() {
      if (ifArchiveTestResults){
        script.echo "## Archive Unit tests report"
        String[] junitReports = script.findFiles(glob: pathToUnitTestResults)
        if (junitReports.length > 0){
          script.junit pathToUnitTestResults
        }
        // Since tests results are already archived, no need to archive them again
        ifArchiveTestResults = false
      }
    }

    void archiveFiles() {
        if (artifactsList != ""){
          script.archiveArtifacts artifacts: artifactsList, excludes: artifactsListExcludes
        }
    }

    void uploadToGS(String uploadFolderName){
      script.echo "### Upload to GS"
      stageName = script.env.STAGE_NAME
      Utils.uploadToGS( script, projectName, uploadFolderName, versionName, artifactsForUpload)

    }

    void slackNotify(boolean startMessage=false){
      if (ifNotify) {
          def notify = new Notify(script)
          String tokenName = "slack_jenkins_ci"
          additionalSlackMessage = (script.currentBuild.currentResult == "SUCCESS" && !startMessage) ? additionalSlackMessage + "\n" + additionalSuccessSlackMessage : additionalSlackMessage
          notify.slackNotify(slackChannel, tokenName, branchName, versionName, versionCode, isGitRepo, additionalSlackMessage, startMessage, stageName)
      }
    }

}
