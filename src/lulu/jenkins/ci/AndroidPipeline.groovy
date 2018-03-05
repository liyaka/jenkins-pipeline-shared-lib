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
    def appAndroidTestsAPK = "**/app-release-androidTest.apk"

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

    String getFullApkPath(String apkNamePattern){
      def appAPKName = script.findFiles(glob: apkNamePattern)
      String apkFullName = appAPKName[0]
      return apkFullName
    }

    @Override
    void integrationTests(){
      logger.info "### Run Integration Tests"
      def resultsName = "${script.env.JOB_NAME}_${script.env.BUILD_NUMBER}".toLowerCase()

      try {
        // Runs with choosen device Nexus5, api version 23, locale en, orientation portrait
        // If needed can be run with multiple variations of devices/version/locale/orientation, bit at the moment resluts are copied
        // only for one, since the folder created by Firebase is built from this parameters
        def device_model = "Nexus5"
        def api_version="23"
        def locale="en"
        def orientation="portrait"

        script.sh "gcloud firebase test android run --app " + getFullApkPath(appForTestAPK) + " --test " + getFullApkPath(appAndroidTestsAPK) +
                  " --device model=${device_model},version=${api_version},locale=${locale},orientation=${orientation} --directories-to-pull /sdcard/Download/report --results-history-name ${resultsName} --results-bucket ${resultsName} --results-dir=${resultsName}"
      } catch (e) {

          logger.info "${e}"

      } finally {
        try {
          logger.info "## Copy tests results"
          script.sh "gsutil -m cp -r gs://${resultsName}/${resultsName}/${device_model}-${api_version}-${locale}-${orientation}/ ."
          artifactsList = artifactsList + ",${device_model}-${api_version}-${locale}-${orientation}/**/*"

          logger.info "### Instrumentation test log:"
          logger.info "#############################"
          script.sh "cat ${device_model}-${api_version}-${locale}-${orientation}/instrumentation.results"
          logger.info "#############################"

          // TODO - if no report-json file is found report can not be created, should not fail the build
          script.junit testResults: "${device_model}-${api_version}-${locale}-${orientation}/test_result_0.xml"
          script.sh "mv ${device_model}-${api_version}-${locale}-${orientation}/artifacts/report-json ${device_model}-${api_version}-${locale}-${orientation}/artifacts/report.json"
          script.cucumber fileIncludePattern: '*.json', jsonReportDirectory: "${device_model}-${api_version}-${locale}-${orientation}/artifacts"

          logger.info "Remove bucket with test results"
          script.sh "gsutil -m rm -r gs://${resultsName}"
        } catch (ee) {
          logger.info "### Some test results files are missing. Error: " + ee.toString()
          if (script.currentBuild.result != "FAILED"){
            logger.info "### Setting build status to UNSTABLE"
            script.currentBuild.result = "UNSTABLE"
          }

        }

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
