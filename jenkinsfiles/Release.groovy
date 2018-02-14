
@Library('lulu-jenkins-pipeline-libs')
import lulu.jenkins.utils.*
import lulu.jenkins.ci.*

def checkoutRepo(String projectName, String branchName){

    echo "### Checkout ${projectName}"
    checkout([$class: 'GitSCM',
              branches: [[name: "*/${branchName}"]],
              browser: [$class: 'GithubWeb', repoUrl: "https://github.com/myorg/${projectName}"],
              extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: projectName],
                           [$class: 'LocalBranch', localBranch: branchName ]],
                           userRemoteConfigs: [[url: "git@github.com:myorg/${projectName}.git"]]])
}

def tagNArchive(String projectName, String fullReleaseVersion){

  echo "### Create git tag and archives for ${projectName}"
  dir("${projectName}"){
    sh "git tag -f ${fullReleaseVersion}"
    sh "git push origin ${fullReleaseVersion}"
    sh "tar cvfz ${env.WORKSPACE}/v${fullReleaseVersion}-${projectName}.tar.gz --exclude .git ."
  }
}

node ("master") {

  timestamps(){
    logger = new Logger(this)
    logger.info "### Running Release flow"

    def releaseBranchName = "release/${params.RELEASE_NAME}"
    def fullReleaseVersion = "R.${params.RELEASE_NAME}.${env.BUILD_NUMBER}"
    logger.info "fullReleaseVersion is ${fullReleaseVersion}"
    currentBuild.displayName = fullReleaseVersion

    stage("Clean Workspace") {
      logger.info "## Cleaning workspace"
      deleteDir()
    }

    stage("Checkout"){
      logger.info("## Checkout")
      checkoutRepo("StaticCodeRepo1", releaseBranchName )
      checkoutRepo("StaticCodeRepo2", releaseBranchName )
      checkoutRepo("StaticCodeRepo3", releaseBranchName )
      checkoutRepo("StaticCodeRepo4", releaseBranchName )
    }

    def Android1BuildNumber
    def Android2BuildNumber

    stage("Build APKs") {
      logger.info("## Build APKs")
      parallel (
          "Android1" : {
              Android1BuildNumber = build job: "Android1-release", parameters: [[$class: 'StringParameterValue', name: 'BRANCH_NAME', value: releaseBranchName],
                                                          [$class: 'StringParameterValue', name:'RELEASE_BUILD_NUMBER', value: env.BUILD_NUMBER]]
          },
          "Android2" : {
              Android2BuildNumber = build job: "Android2-release", parameters: [[$class: 'StringParameterValue', name: 'BRANCH_NAME', value: releaseBranchName],
                                                                          [$class: 'StringParameterValue', name:'RELEASE_BUILD_NUMBER', value: env.BUILD_NUMBER]]
          }
      )
      logger.info "Android1BuildNumber is ${Android1BuildNumber.getNumber()}"
      logger.info "Android2BuildNumber is ${Android2BuildNumber.getNumber()}"
    }

    stage("Copy apks"){
      logger.info("## Copy apks")
      copyArtifacts filter: '**/*--Android1.apk, *_version', fingerprintArtifacts: true, flatten: true, projectName: 'Android1-release', selector: specific("${Android1BuildNumber.getNumber()}")
      copyArtifacts filter: '**/*--Android2.apk, *_version', fingerprintArtifacts: true, flatten: true, projectName: 'Android2-release', selector: specific("${Android2BuildNumber.getNumber()}")

    }

    stage("Archive static code"){
      logger.info("## Archive static code")
      tagNArchive("StaticCodeRepo1", fullReleaseVersion)
      tagNArchive("StaticCodeRepo2", fullReleaseVersion)
      tagNArchive("StaticCodeRepo3", fullReleaseVersion)
      tagNArchive("StaticCodeRepo4", fullReleaseVersion)
    }

    stage("Archive artifacts"){
      logger.info "## Archive artifacts"
      archiveArtifacts artifacts: "*.apk, *.tar.gz", excludes: ""
    }

    stage("Upload to GDrive") {
      logger.info("## Upload to GDrive")

      def apkForUpload = findFiles(glob: '*.apk')
      def tarForUpload = findFiles(glob: '*.tar.gz')
      def artifactsForUpload = apkForUpload + tarForUpload
      def releasesFolderName = "Releases"

      def gdrive = new GoogleDrive(this)
      logger.info "Create a new release folder ${fullReleaseVersion}"
      gdrive.createFolder(fullReleaseVersion, releasesFolderName)

      for (int i = 0; i < artifactsForUpload.size(); i++) {
          gdrive.uploadToGDrive( artifactsForUpload[i].name, fullReleaseVersion)
      }

    }

    stage ("Notify"){
      def notify = new Notify(this)
      notify.slackNotify("#jenkins-release", "slack_jenkins_release", releaseBranchName, fullReleaseVersion)
    }
  }


}
