package lulu.jenkins.ci

import lulu.jenkins.ci.Constants
import lulu.jenkins.utils.*

abstract class BasePipeline implements Serializable {

    def script
    def logger
    def orgName
    def projectName
    def branchName
    def firstUnstableStage
    def pauseAfterEachStage
    def versionName
    def artifactsList
    def artifactsListExcludes = ""
    def uploadFolderName
    def ifNotify = true
    def ifRelease = false

    BasePipeline(script) {
        this.script = script

        logger = new Logger(script)
    }

    void setBranchProperties(String branchType){

      if (branchType == "master") {
        branchName = "master"
        uploadFolderName = Constants.GOOGLE_DRIVE_NIGHTLY_FOLDER
      } else {
        branchName = "${script.env.BRANCH_NAME}".replaceAll("origin/","")
        if (branchName.startsWith("release") && "${script.env.RELEASE_BUILD_NUMBER}" != "" && "${script.env.RELEASE_BUILD_NUMBER}" != "null"){
          uploadFolderName = Constants.GOOGLE_DRIVE_RELEASE_FOLDER
          ifRelease = true
        } else {
          uploadFolderName = Constants.GOOGLE_DRIVE_FB_FOLDER
        }
      }

    }

    void run() {
        script.timestamps() {
            try {
                logger.info "## Running with params: ${this.properties}"
                runImpl()
                if (ifRelease){
                  script.stage ("TagBuild"){
                    logger.info "### TagBuild"
                    gitTagforBuild()
                  }
                }
            } catch (e) {
                script.currentBuild.result = "FAILED"
                throw e
            } finally {
                script.stage ("Archive"){
                  logger.info "### Archive"
                  // archiveTestResults()
                  archiveFiles()
                }
                script.stage ("Notify"){
                  logger.info "### Notify"
                  setBuildStatusInGit()
                  if (ifNotify) {
                    def notify = new Notify(script)
                    notify.slackNotify("#jenkins_ci", "slack_jenkins_ci", branchName, versionName)
                  }
                }
                // script.stage ('Final') {
                //   logger.info "### Final"
                  //cleanupResources()
                }
                if (firstUnstableStage) {
                  logger.info "## Build became UNSTABLE in stage $firstUnstableStage"
                }
            }
        }


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
        logger.info "### Setup"
        initParams()
        cleanWorkspace()

    }

    void checkout() {

       logger.info "### Checkout"

       script.checkout changelog: true,
                scm: [
                $class           : 'GitSCM',
                branches         : [[name: branchName]],
                extensions       : [[$class: 'LocalBranch', localBranch: "**"],
                                   [$class: 'SubmoduleOption',
                                    disableSubmodules: true,
                                    parentCredentials: true,
                                    recursiveSubmodules: true,
                                    reference: '',
                                    trackingSubmodules: false]],
                browser          : [$class: 'GithubWeb', repoUrl: Constants.GITHUB_BROWSE_URL + orgName + "/" + projectName ],
                userRemoteConfigs: [[url: Constants.GITHUB_URL + orgName + "/" + projectName + '.git' ]]

        ]

        script.sh "git submodule update --recursive --init"

    }

    void initParams() {
    }

    void retrieveCurrentBuildUser() {
        script.wrap([$class: 'BuildUser']) {
            script.sh returnStdout: true, script: 'echo ${BUILD_USER}'
        }
    }

    void getGitUserFromBranchName() {
      if (branchName  != "") {
          script.sh returnStdout: true, script: 'dirname ${branchName } | xargs basename'
      } else {
          script.sh returnStdout: true, script: 'echo ""'
      }
    }

    void retrieveBranchName(){
      script.sh returnStdout: true, script: 'git branch'
    }

    void buildInfo() {
        logger.info "### BuildInfo"
        setVersion()
        populateBuildDisplayName()
        populateBuildDescription()
        createVersionFile()
    }

    void setVersion(){

      def versionBase = script.sh (returnStdout: true, script:'#!/bin/bash \n while read var value; do export ${var}=${value//\\"/}; done < version-app; echo $versionName').trim()
      logger.info "## versionBase is ${versionBase}, branchName is ${branchName}, ifRelease is ${ifRelease}"
      if (branchName == "master") {
        versionName = "${versionBase}.${script.env.BUILD_NUMBER}"
      } else {
        if (ifRelease) {
          def fixedBranchName = branchName.replaceAll('release/',"").replaceAll('/',"_")
          versionName = "R.${fixedBranchName}.${script.env.RELEASE_BUILD_NUMBER}"
        } else {
          def fixedBranchName = branchName.replaceAll('/',"_")
          versionName = "${versionBase}.${fixedBranchName}.${script.env.BUILD_NUMBER}"
        }
      }
      logger.info "## Version is ${versionName}"

    }

    void populateBuildDisplayName() {
        script.currentBuild.displayName = versionName
    }

    void populateBuildDescription() {
        if (branchName.startsWith("release") || branchName == "master"){
          script.currentBuild.description = ""
        } else {
          script.currentBuild.description = "Branch: <b>${branchName}</b>"
        }
    }

    void createVersionFile(){
      def projectNameUpper = projectName.toUpperCase()
      def version_map = [ (projectNameUpper + "_VERSION"): "${versionName}",
                          (projectNameUpper + "_GIT_COMMIT"): "${getCommitSha()}"]
      script.writeYaml file: projectName + "_version", data: version_map
    }

    void setBuildStatusInGit() {
      // Workaround - there is no UNSTABLE status that can be sent to GitHub, change it to ERROR
      def buildSts = script.currentBuild.currentResult
      if (buildSts == "UNSTABLE"){
        buildSts = "ERROR"
      }
      // need to have jenkins_github_login_user_token defined in your credetials (create the token for user on GitHub in advance)
      logger.info("repo: ${projectName}, account: \"myorg\", context: 'build_sts', credentialsId: \"jenkins_github_login_user_token\", sha: \"${getCommitSha()}\", description: 'Build Status', status: ${buildSts}")
      script.githubNotify account: 'myorg', context: 'build_sts', credentialsId: 'jenkins_github_login_user_token', description: 'Build Status', repo: projectName, sha: getCommitSha(), status: buildSts

    }

    void setGitBranch( String inputGitBranch = ""){
      if (inputGitBranch != "") {
        branchName  = inputGitBranch
      } else {
        branchName  = "${retrieveBranchName()}"
      }

    }

    String getCommitSha(){
      return script.sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    }

    void deploy(){
        logger.info  "Implements deploy logic here (push to docker , maven, gradle deploy)"
    }

    void build() {

    }

    void unitTests(String unitTestArgs) {
        // Implement in CIs where UTs are not being run during compile (i.e. non maven builds)
    }

    void systemTests() {}

    void unitTests() {}

    void integrationTests() {}


    void runTests() {
        runSystemTests()
        archiveTestScreenshots()
        archiveTestResults()
        // Archiving test results can change script.currentBuilt.result

    }

    void runSystemTests() {

    }

    void archiveTestScreenshots() {
    }

    void uploadArtifact(){
        logger.info "## Implements uploading artifacts to various repositories (push to docker , maven, gradle deploy)"
    }

    void archiveLogs() {
    }

    void cleanupResources() {
      logger.info "## Change permissions to be able to delete the workspace"
      script.sh 'sudo chown -R jenkins:jenkins *'
    }

    void cleanWorkspace() {
      logger.info "## Cleaning workspace"
      script.deleteDir()
    }

    void archiveTestResults() {
      script.step([$class: 'JUnitResultArchiver', testResults: 'output/test_reports/*/*.xml', allowEmptyResults: true])
    }

    void archiveFiles(){
      script.archiveArtifacts artifacts: artifactsList, excludes: artifactsListExcludes
    }

    String getCurrentBuildResult() {
        return script.currentBuild.result
    }

    void gitTagforBuild(){
      script.sh("git tag -f ${versionName}")
      script.sh("git push origin ${versionName}")
    }


    // @NonCPS
    // def mapToList(depmap) {
    //     def dlist = []
    //     for (def entry2 in depmap) {
    //         dlist.add(new java.util.AbstractMap.SimpleImmutableEntry(entry2.key, entry2.value))
    //     }
    //     dlist
    // }
}
