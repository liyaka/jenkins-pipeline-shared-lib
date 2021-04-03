package lulu.jenkins.ci

class Constants {

    static final GITHUB_URL = "git@github.com:"
    static final GITHUB_BROWSE_URL = "https://github.com/"
    static final ORG_NAME = "liyaka" // You ORG

    static final PROD_DOCKER_REGISTRY = "reg-prod" // Set yours
    static final DEV_DOCKER_REGISTRY = "reg-dev" // Set yours

    static final K8S_CLUSTER_NAME_DEV = "dev-cluster"
    static final K8S_CLUSTER_NAME_PROD = "prod-cluster"
    static final K8S_GOOGLE_PROJECT_DEV = "env-dev"
    static final K8S_GOOGLE_PROJECT_PROD = "env-prod"
    static final K8S_ZONE_DEV = "us-central1-a"
    static final K8S_ZONE_PROD = "us-central1-c"

}
