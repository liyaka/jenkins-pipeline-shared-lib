package lulu.jenkins.utils;

import lulu.jenkins.utils.Logger

class GoogleDrive implements Serializable {
    def script
    def logger

    GoogleDrive(script) {
        this.script = script

        logger = new Logger(script)
    }

    String getFolderId(String folderName) {

        String folder_id = script.sh (
          script: "gdrive list --name-width 0 --no-header --query \"mimeType='application/vnd.google-apps.folder'andname='${folderName}'\" | awk '{print \$1}' ",
          returnStdout: true
        ) .trim()

        return folder_id

    }

    String createFolder(String newFolderName, String parentFolderName ){
      def parentFolderId = this.getFolderId(parentFolderName)
      if (parentFolderId == ""){
        script.error "There was a problem with finding the ${uploadFolderName} folder in google drive. Failing the build!"
      }

      String folder_id = script.sh (
        script: "gdrive mkdir -p ${parentFolderId} ${newFolderName} | awk '{print \$1}'",
        returnStdout: true
      ) .trim()

      return folder_id
    }

    void uploadToGDrive(String uploadFileNameSuffix, String uploadFolderName ){

      logger.info "## uploadFileNameSuffix: ${uploadFileNameSuffix}"
      def uploadFilePath = script.sh (returnStdout: true, script:"ls ${uploadFileNameSuffix} 2> /dev/null").trim()
      logger.info "## uploadFilePath is ${uploadFilePath}"
      if (uploadFilePath != "") {
          def uploadFolderId = this.getFolderId(uploadFolderName)
          if (uploadFolderId == ""){
            script.error "There was a problem with finding the ${uploadFolderName} folder in google drive. Failing the build!"
          }
          try {
            def uploadFileName = script.sh (returnStdout: true, script: "basename '${uploadFilePath}'").trim()
            script.sh """gdrive upload --parent ${uploadFolderId} --name "${uploadFileName}" "${uploadFilePath}" """
          } catch (e) {
            script.error "Upload to GoogleDrive folder ${uploadFolderName} has failed with error " + e
          }
          logger.info "## File ${uploadFilePath} is uploaded to GoogleDrive folder ${uploadFolderName} "

      } else {
        script.error "For some reason, ${uploadFileNameSuffix} was not created. Check the compilation log"
      }
    }

}
