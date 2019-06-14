# Amazon Elastic Container Service Plugin for Jenkins

[![Build Status](https://ci.jenkins.io/job/Plugins/job/amazon-ecs-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/amazon-ecs-plugin/job/master/)
[![Join the chat at https://gitter.im/jenkinsci/amazon-ecs-plugin](https://badges.gitter.im/jenkinsci/amazon-ecs-plugin.svg)](https://gitter.im/jenkinsci/amazon-ecs-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)


## About

This Jenkins plugin do use [Amazon Elastic Container Service](http://docs.aws.amazon.com/AmazonECS/latest/developerguide/Welcome.html) to host jobs execution inside docker containers.

* see [Jenkins wiki](https://wiki.jenkins-ci.org/display/JENKINS/Amazon+EC2+Container+Service+Plugin) for detailed feature descriptions
* use [GitHub Issues](https://github.com/jenkinsci/amazon-ecs-plugin/issues) to report issues / feature requests


## Documentation and Installation

Please find the documentation on the [Jenkins Wiki page Amazon EC2 Container Service Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Amazon+EC2+Container+Service+Plugin).

## Declarative Pipeline
Declarative Pipeline support requires Jenkins 2.66+  

Declarative agents can be defined like shown below. You can also reuse pre-configured templates and override certain settings using `inheritFrom` to reference the Label field of the template that you want to use as preconfigured. Only one label is expected to be specified.

_Note_: You have to configure list of settings to be allowed in the declarative pipeline first (see the Allowed Overrides setting). They are disabled by default for security reasons, to avoid non-privileged users to suddenly be able to change certain settings.

```groovy
pipeline {
  agent none

  stages {
    stage('Test') {
        agent {
            ecs {
                inheritFrom 'my-preconfigured-template'
                cpu 2048
                memory 4096
                logDriver 'fluentd'
                logDriverOptions([[name: 'foo', value:'bar'], [name: 'bar', value: 'foo']])
            }
        }
        steps {
            sh 'echo hello'
        }
    }
  }
}
```

## FAQ
### My parallel jobs don't start at the same time
Actually, there can be multiple reasons:

* The plugin creates a new agent only when the stage contains an `agent` [definition](https://jenkins.io/doc/book/pipeline/syntax/#agent). If this is missing, the stage inherits the agent definition from the level above and also re-uses the instance. 

* Also, parallel stages sometimes don't really start at the same time. Especially, when the provided label of the `agent` definition is the same. The reason is that Jenkins tries to guess how many instances are really needed and tells the plugin to start n instances of the agent with label x. This number is likely smaller than the number of parallel stages that you've declared in your Jenkinsfile. Jenkins calls the ECS plugin multiple times to get the total number of agents running.

* If launching of the agents takes long, and Jenkins calls the plugin in the meantime again to start n instances, the ECS plugin doesn't know if this instances are really needed or just requested because of the slow start. That's why the ECS plugin subtracts the number of launching agents from the number of requested agents (for a specific label). This can mean for parallel stages that some of the agents are launched after the previous bunch of agents becomes online.



## Maintainers
Andreas Sieferlinger ([GitHub](https://github.com/webratz) [Twitter](https://twitter.com/webratz))  
Philipp Garbe ([GitHub](https://github.com/pgarbe), [Twitter](https://twitter.com/pgarbe))  
Marky Jackson ([GitHub](https://github.com/markyjackson-taulia), [Twitter](https://twitter.com/MarkyJackson3))

## Developing

Building the Plugin

```bash
  $ java -version # Need Java 1.8, earlier versions are unsupported for build
  $ mvn -version # Need a modern maven version; maven 3.2.5 and 3.5.0 are known to work
  $ mvn clean install
```

To run locally, execute the following command and open the browser [http://localhost:8080/jenkins/](http://localhost:8080/jenkins/)

```bash
  $ mvn -e hpi:run
```

Releasing the Plugin

```bash
 $ mvn release:prepare release:perform
```
