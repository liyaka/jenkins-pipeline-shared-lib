package lulu.jenkins.utils;

import lulu.jenkins.ci.Constants

class GitUtils implements Serializable {

  static void checkout(script, String orgName, String projectName, String branchName, String targetDir="", boolean shallowClone = false, boolean submodulesUpdate = true) {

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
                                  trackingSubmodules: false],
                                [$class: 'CloneOption', noTags: false, reference: '', shallow: shallowClone],
                                [$class: 'CheckoutOption', timeout: 30],
                                [$class: 'UserExclusion', excludedUsers: 'IR-Jenkins'],
                                [$class: 'MessageExclusion', excludedMessage: '.*Update submodules pointers to latest.*'],
                                [$class: 'RelativeTargetDirectory', relativeTargetDir: targetDir ]],
              browser          : [$class: 'GithubWeb', repoUrl: Constants.GITHUB_BROWSE_URL + orgName + "/" + projectName ],
              userRemoteConfigs: [[url: Constants.GITHUB_URL + orgName + "/" + projectName + '.git' ]]

      ]

      if (submodulesUpdate){
        if ( targetDir != ""){
          script.dir( targetDir ) {
            script.sh "git submodule update --recursive --init"
          }
        } else {
          script.sh "git submodule update --recursive --init"
        }
      }

  }

  static void setBuildStsinGitHub(script, String projectName){
    // Workaround - there is no UNSTABLE status that can be sent to GitHub, change it to ERROR
    def buildSts = script.currentBuild.currentResult
    if (buildSts == "UNSTABLE") {
        buildSts = "ERROR"
    }
    String commitSha1 = getCommitSha(script)
    script.echo("repo: ${projectName}, account: " + Constants.ORG_NAME + ", context: 'commit_build_sts', credentialsId: \"jenkins_user_github_login\", sha: \"${commitSha1}\", description: 'Build Status of Commit', status: ${buildSts}")
    script.githubNotify account: Constants.ORG_NAME, context: 'commit_build_sts', credentialsId: 'jenkins_user_github_login', description: 'Build Status of Commit', repo: projectName, sha: commitSha1, status: buildSts
  }

  static String getCommitUser(script) {
      return script.sh(returnStdout: true, script: "git --no-pager show -s --format='%an' HEAD").trim()
  }

  static String getCommitUserMail(script) {
      return script.sh(returnStdout: true, script: "git --no-pager show -s --format='%ae' HEAD").trim()
  }

  static String getCommitSha(script) {
    return script.sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
  }

  static void tagBuild(script, String tagName) {
      script.sh("git tag -f ${tagName}")
      script.sh("git push origin ${tagName} || true")
  }

}
