package lulu.jenkins.utils;

import org.apache.commons.lang3.StringUtils
import lulu.jenkins.ci.Constants
import java.util.Date
import java.text.SimpleDateFormat

class Utils implements Serializable {

    static void cleanWorkspace(script) {
        if ("${script.env.CLEAN_WORKSPACE}" != "false"){
          def currDir = script.pwd()
          script.echo "## Cleaning workspace ${currDir}"
          script.deleteDir()
        }
      }

    static boolean isProjectLastBuildSuccessful(script, String jobName) {
      try {
          def job = Jenkins.getInstance().getItemByFullName(jobName, Job.class)
          int lastBuild = job.getLastBuild().getNumber()
          int lastSuccessfulBuild = job.getLastSuccessfulBuild().getNumber()
          if (lastBuild != lastSuccessfulBuild){
              script.echo ("Last ${jobName} build is not successful!!")
              return false
          }
         return true
      } catch (Exception e) {
          script. error "${e}"
      }
    }

    static String getVersionBaseFromVersionFile(script, String versionFile = "version-base.txt"){
      script.echo "versionFile is ${versionFile}"
      return script.sh(returnStdout: true, script: "cat ${versionFile}").trim()
    }

    static String incrementVersionName( script, String originalVersion, String incrementType ){
      script.echo "originalVersion is ${originalVersion}, incrementType is ${incrementType}"
      String[] originalVersionArr = originalVersion.split("\\.")
      String newVersion = ""
      if (incrementType == "patch"){
        originalVersionArr[2] = (originalVersionArr[2].toInteger() + 1).toString()
      } else if (incrementType == "minor"){
        originalVersionArr[1] = (originalVersionArr[1].toInteger() + 1).toString()
        originalVersionArr[2] = "0"
      } else {
        script.echo "Type ${incrementType} is undefined"
      }

      newVersion = originalVersionArr.join(".")
      script.echo "newVersion is ${newVersion}"
      return newVersion
    }

    static boolean checkLogForString (script, String filterString, int occurrence){
      def logs = script.currentBuild.rawBuild.getLog(Integer.MAX_VALUE).join('\n')
      int count = StringUtils.countMatches(logs, filterString)
      if (count > occurrence -1) {
        return true
      } else {
        return false
      }
    }

    static def getLinesFromLog (script, String logString){
      def logs = script.currentBuild.rawBuild.getLog(Integer.MAX_VALUE)
      def linesWithString = []
      for (line in logs){
        if ((line.trim()).contains(logString)){
          linesWithString.add(line.replaceAll("\\[.+\\] ","").replace("\"",""))
        }
      }
      return linesWithString

    }

    static void uploadToGS(script, String projectName, String uploadFolderName, String versionName, String artifactsForUpload ){
        if (uploadFolderName == "" || artifactsForUpload == "") {
            script.echo "## No files will be uploaded to GS"
            return
        }

        String gsFolderName = Constants.GS_BUCKET + "/${projectName}/${uploadFolderName}/" + versionName
        script.echo "gsFolderName: ${gsFolderName}"
        script.googleStorageUpload bucket: gsFolderName, credentialsId: Constants.GS_BUCKET_CREDENTIALS, pattern: artifactsForUpload, showInline: true

    }

    static def formatDate( String parseDate, String dateFormat){
      return new SimpleDateFormat(dateFormat).parse(parseDate);
    }

    static def getKeepDate(script, String thresholdInDays){
      String now = new Date()
      String keepDay = script.sh (script: "date -d '${now} -${thresholdInDays} days'",returnStdout: true).trim()
      script.echo "now is ${now} and keepDay is ${keepDay}"
      return formatDate(keepDay, "EEE MMM dd HH:mm:ss z yyyy")
    }

    static void retrieveCurrentBuildUser(script) {
        script.wrap([$class: 'BuildUser']) {
            script.sh returnStdout: true, script: 'echo ${BUILD_USER}'
        }
    }

    static int getRunDuration(script, String lastUpdate){
        def formatter = new SimpleDateFormat("dd-MM-yyyy")
        Date lastRun = formatter.parse(lastUpdate)
        Date now = new Date()
        def duration = now - lastRun
        script.echo "The duration $duration"
        return duration
    }

    // @NonCPS
    // static def mapToList(script, depmap) {
    //     def dlist = []
    //     for (def entry2 in depmap) {
    //         dlist.add(new java.util.AbstractMap.SimpleImmutableEntry(entry2.key, entry2.value))
    //     }
    //     dlist
    // }

}
