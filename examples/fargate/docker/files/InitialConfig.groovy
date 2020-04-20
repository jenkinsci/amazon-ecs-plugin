import jenkins.model.*
Jenkins.instance.setNumExecutors(0)

def instance = Jenkins.getInstance()
instance.setInstallState(InstallState.INITIAL_SETUP_COMPLETED)
