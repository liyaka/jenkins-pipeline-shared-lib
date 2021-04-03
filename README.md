# jenkins-pipeline-shared-lib
Jenkins pipeline shared library

Example for Jenkins Pipeline shared library.

- CI
   - Constants
   - BasePipeline
   - K8sServicePipeline - builds only Dockerfile, based on assumption that helm chart is a art of the service repo 
- Deployment:
   - Deploy k8s service class
   - Promote k8s service to production
- Admin:
  - Delete Namespaces - deletes dynamically created namespaces for dev environments
- Utils:
  - Notify - notify to Slack
  - Utils - generic static functions
  - GitUtils - static functions for git actions
  - K8sUtils - static functions for K8s actions

Support for:
- git - GitHub
- K8s cluster - GKE

Jenkinsfile examples:
- Jenkinsfile - for building a k8s service
- Jenkinsfile.deploy - for deploying a k8s service


Note:
- Job parameters are not defined in code because I wanted them to be different for branch/master/branch jobs - can be implemented in code, but make it much more complicated and unstable...


TODO:
- add the same support for Bitbucket
- add the same support for EKS
