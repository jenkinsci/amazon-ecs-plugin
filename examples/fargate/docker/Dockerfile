FROM jenkins/jenkins:lts
# ENV CASC_JENKINS_CONFIG /usr/share/jenkins/jenkins.yaml
COPY files/jenkins.yaml /var/jenkins_home/jenkins.yaml
RUN echo 2.0 > /usr/share/jenkins/ref/jenkins.install.UpgradeWizard.state
COPY files/InitialConfig.groovy /usr/share/jenkins/ref/init.groovy.d/InitialConfig.groovy
COPY files/plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN /usr/local/bin/install-plugins.sh < /usr/share/jenkins/ref/plugins.txt
