import jenkins.model.*
import jenkins.install.InstallState
Jenkins.instance.setNumExecutors(0)

Jenkins.instance.setInstallState(InstallState.INITIAL_SETUP_COMPLETED)



// setting the Jenkins URL
url = System.env.JENKINS_URL
urlConfig = JenkinsLocationConfiguration.get()
urlConfig.setUrl(url)
urlConfig.save()
