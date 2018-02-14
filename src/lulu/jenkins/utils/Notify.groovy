package lulu.jenkins.utils;

class Notify implements Serializable {
    def script


    Notify(script) {
        this.script = script
    }

    void slackNotify(String channelName, String tokenName, String branchName, String versionName){

      def buildStatus = script.currentBuild.currentResult
      def buildDuration =  script.currentBuild.durationString.replaceAll("and counting","")
      def changelog = getChangeSet()
      script.echo "buildStatus: " + buildStatus

      def colorCode = '#FF0000' // RED

      if (buildStatus == 'UNSTABLE') {
          colorCode = '#FFFF00' // yellow
      } else if (buildStatus == 'SUCCESS') {
          colorCode = '#00CF00' // green
      } else if (buildStatus == 'ABORTED') {
        colorCode = '#AAAAAA' // grey
      }

      def message = "*${script.env.JOB_NAME}* - #${script.env.BUILD_NUMBER} ${buildStatus} after ${buildDuration}  (<${script.env.BUILD_URL}|Open>) \nBranch: ${branchName} , version: *${versionName}*\nChanges: \n${changelog}"

      script.slackSend (color: colorCode, channel: channelName, message: message , tokenCredentialId: tokenName)

    }

    @NonCPS
    // Fetching change set from Git
    def getChangeSet() {
      return script.currentBuild.changeSets.collect { cs ->
        cs.collect { entry ->
            "- ${entry.msg} [_${entry.author.fullName}_]"
        }.join("\n")
      }.join("\n")
    }


}
