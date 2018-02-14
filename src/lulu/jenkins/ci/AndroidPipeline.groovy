package lulu.jenkins.ci;

import lulu.jenkins.ci.*

import lulu.jenkins.utils.GoogleDrive

class AndroidPipeline extends BasePipeline {
    boolean debugMode
    def buildTarget
    // Map of submodules repositories
    def submodulesRepos
    def submodulesBranch = "master"
    // Map with original name and name to copy to
    def artifactsForUpload
    def ifUploadToGDrive = true
    def ifRenameArtifacts = true
    def runIntegrationTests = false
    def appForTestAPK
    def appPackageName
    def appAndroidTestsAPK = "app-release-androidTest.apk"

    AndroidPipeline(script) {
        super(script)
    }

    @Override
    void runImpl() {
        script.stage('Setup', this.&setup)
        script.stage('Checkout', this.&checkout)
        script.stage('BuildInfo', this.&buildInfo)
        script.stage('Build', this.&build)
        // script.stage('Unit Tests', this.&unitTests)
        if ( runIntegrationTests ) {
          script.stage('Integration Tests', this.&integrationTests)
        }
        //script.stage('Deploy To Nexus', this.&uploadArtifact)
        script.stage('Upload to GDrive', this.&uploadToGDrive)

    }

    // ENFOURCE_VERSION_NAME - variable that is used in gradle build to set a version in created artifacts names
    @Override
    void build() {
        logger.info "### Starting Gradle build, buildTarget is ${buildTarget}"
        script.withEnv(["ENFOURCE_VERSION_NAME=${versionName}"]) {
          script.sh "./gradlew $buildTarget "
        }
    }

    void uploadToGDrive() {
      logger.info "### Upload to GDrive"

      if (ifUploadToGDrive && ifRelease){
        ifUploadToGDrive = false
      }
      //def timestamp = Calendar.getInstance().getTime().format('YYYY-MM-dd-hhmmss')

      if (ifUploadToGDrive) {

        def gdrive = new GoogleDrive(script)
        for (int i = 0; i < artifactsForUpload.size(); i++) {
            gdrive.uploadToGDrive( artifactsForUpload[i], uploadFolderName)
        }
      } else {
        logger.info "## No files will be uploaded to GoogleDrive"
      }

    }

  }
