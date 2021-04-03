package lulu.jenkins.utils;

import hudson.tasks.test.AbstractTestResultAction
import groovy.json.JsonSlurper

class Notify implements Serializable {
    def script


    Notify(script) {
        this.script = script
    }

    void slackNotify(String channelName, String tokenName, String branchName, String versionName, String versionCode="", boolean isGitRepo=true, String additionalMessage="", boolean startMessage=false, String failedInStage = ""){

      String message = ""
      String colorCode = ""
      String fullVersion = ( versionCode != "" ) ?  "${versionName}-${versionCode}" : "${versionName}"
      def buildStatus = script.currentBuild.currentResult

      if (startMessage){

        colorCode = "#3b6ad9"
        message = "*${script.env.JOB_NAME}* - #${script.env.BUILD_NUMBER} (<${script.env.BUILD_URL}|Open>)\nBranch: ${branchName} , version: *${fullVersion}*\n${additionalMessage}"

      } else {


        def buildDuration =  script.currentBuild.durationString.replaceAll("and counting","")
        def changelog = (isGitRepo) ? getChangeSet() : ""
        script.echo "buildStatus: " + buildStatus

        colorCode = '#FF0000' // RED

        if (buildStatus == 'UNSTABLE') {
            colorCode = '#FFFF00' // yellow
        } else if (buildStatus == 'SUCCESS') {
            colorCode = '#00CF00' // green
        } else if (buildStatus == 'ABORTED') {
          colorCode = '#AAAAAA' // grey
        }

        String testStatus = getTestStatuses()
        failedInStage = (failedInStage != "" && buildStatus == "FAILURE") ? "\nFailed in Stage: *${failedInStage}*" : ""
        message = "*${script.env.JOB_NAME}* - #${script.env.BUILD_NUMBER} ${buildStatus} after ${buildDuration}  (<${script.env.BUILD_URL}|Open>) ${failedInStage} \nBranch: ${branchName} , version: *${fullVersion}*\nChanges: \n${changelog}\n${additionalMessage}${testStatus}"
      }

      script.slackSend (color: colorCode, channel: channelName, message: message , tokenCredentialId: tokenName)
      String errorMessage = ""
      if ( buildStatus == "FAILURE" && script.fileExists ("${script.env.WORKSPACE}/error_message.txt") ){
          errorMessage = script.readFile("${script.env.WORKSPACE}/error_message.txt").trim()
          errorMessage = "\n*Error Message:* ```${errorMessage}```"
          script.slackSend (color: colorCode, channel: channelName, sendAsText: true, message: errorMessage , tokenCredentialId: tokenName)
      }

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

    @NonCPS
    def getTestStatuses() {
        def testStatus = ""
        AbstractTestResultAction testResultAction = script.currentBuild.rawBuild.getAction(AbstractTestResultAction.class)
        if (testResultAction != null) {
            def total = testResultAction.totalCount
            def failed = testResultAction.failCount
            def skipped = testResultAction.skipCount
            def passed = total - failed - skipped
            testStatus = "Test Status:\n  Passed: *${passed}*, Failed: *${failed} ${testResultAction.failureDiffString}*, Skipped: *${skipped}*"

        }
        return testStatus
    }

    def getSlackUserId(String email){
      script.echo "User Mail is: ${email}"
      script.withCredentials([script.string(credentialsId: 'slack_bot_token', variable: 'TOKEN')]) {
        URL apiUrl = "https://slack.com/api/users.lookupByEmail?token=${script.TOKEN}&email=${email}".toURL()
        def json_response = new JsonSlurper().parse(apiUrl.newReader())
        def userId = json_response.user.id
        script.echo "Slack user ID is:  ${userId}"
        return userId
      }
    }

}
