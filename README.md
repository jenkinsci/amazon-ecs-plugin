# Amazon EC2 Container Service Plugin for Jenkins

[![Build Status](https://ci.jenkins.io/job/Plugins/job/amazon-ecs-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/amazon-ecs-plugin/job/master/)
[![Join the chat at https://gitter.im/jenkinsci/amazon-ecs-plugin](https://badges.gitter.im/jenkinsci/amazon-ecs-plugin.svg)](https://gitter.im/jenkinsci/amazon-ecs-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)


## About

This Jenkins plugin do use [Amazon EC2 Container Service](http://docs.aws.amazon.com/AmazonECS/latest/developerguide/Welcome.html) to host jobs execution inside docker containers.

* see [Jenkins wiki](https://wiki.jenkins-ci.org/display/JENKINS/Amazon+EC2+Container+Service+Plugin) for detailed feature descriptions
* use [JIRA](https://issues.jenkins-ci.org/issues/?jql=project%3DJENKINS%20AND%20status%20in%28Open%2C"In%20Progress"%2CReopened%29AND%20component%3Damazon-ecs-plugin) to report issues / feature requests


## Building the Plugin

```bash
  $ java -version # Need Java 1.8, earlier versions are unsupported for build
  $ mvn -version # Need a modern maven version; maven 3.2.5 and 3.5.0 are known to work
  $ mvn clean install
```

To run locally, execute the following command and open the browser [http://localhost:8080/jenkins/](http://localhost:8080/jenkins/)

```bash
  $ mvn -e hpi:run
```

## Maintainers
Philipp Garbe ([GitHub](https://github.com/pgarbe), [Twitter](https://twitter.com/pgarbe))
Douglas Manley ([GitHub](https://github.com/tekkamanendless))
Jan Roehrich ([GitHub](https://github.com/roehrijn))

## Documentation and Installation

Please find the documentation on the [Jenkins Wiki page Amazon EC2 Container Service Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Amazon+EC2+Container+Service+Plugin).
