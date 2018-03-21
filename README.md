# jenkins-pipeline-shared-lib
Jenkins pipeline shared library

Example for Jenkins Pipeline shared library.

- Constants
- BasePipeline
- AndroidPipeline
- DockerPipeline

Utils:
- Logger - generic logger that can be implemented with a specific one
- Notify - notify to Slack
- GoogleDrive - upload files to Google Drive (requires gdrive to be installed and configured on a slave)

Jenkinsfile examples:
- Jenkinsfile-android
- Jenkinsfile-docker
- Jenkinsfile-release - full release flow example for multi-project product


Additional features:
- support for GitHub, supports submodules
- notify to Slack
- upload files to Google drive
- set build description
- build status on Github when build is finished

Note:
- Job parameters are not defined in code because I wanted them to be different for branch/master/release jobs - can be implemented in code, but makes it much more complicated and unstable...


TODO:
- add the same support for Bitbucket
- add support for showing submodules changes
- add mail notifications
