# Amazon Elastic Container Service (ECS / Fargate) Plugin for Jenkins

[![Build Status](https://ci.jenkins.io/job/Plugins/job/amazon-ecs-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/amazon-ecs-plugin/job/master/)
[![Join the chat at https://gitter.im/jenkinsci/amazon-ecs-plugin](https://badges.gitter.im/jenkinsci/amazon-ecs-plugin.svg)](https://gitter.im/jenkinsci/amazon-ecs-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## About

This Jenkins plugin uses [Amazon Elastic Container Service](http://docs.aws.amazon.com/AmazonECS/latest/developerguide/Welcome.html) to host jobs execution inside docker containers.

Jenkins delegates to Amazon ECS the execution of the builds on Docker based agents.
Each Jenkins build is executed on a dedicated Docker container that is wiped-out at the end of the build.

-   use [GitHub Issues](https://github.com/jenkinsci/amazon-ecs-plugin/issues) to report issues / feature requests

## Installation & configuration

The scope of the plugin is only using existing and pre-configured AWS Infrastructure. It does not create any of the needed infrastructure on its own. Use tools like CloudFormation or Terraform for this task.

### Requirements

-   Jenkins with at least version 2.289.1
-   AWS Account


### Plugin install

Use the Jenkins plugin manager to install the [Amazon Elastic Container Service plugin](https://plugins.jenkins.io/amazon-ecs)

### Configuration

#### Examples

There are currently the following example setups in this repo:

-   [Fargate](examples/fargate) - ephemeral master and agents on Fargate
-   [Fargate with CDK (by AWS)](https://github.com/aws-samples/jenkins-on-aws) - Jenkins Setup designed by AWS

Additionally there is an example setup here: [Terraform Jenkins AWS ECS Fargate](https://github.com/haidaraM/terraform-jenkins-aws-fargate)

#### Amazon ECS cluster

As a pre-requisite, you must have created an Amazon ECS cluster with associated ECS instances. These instances can be statically associated with the ECS cluster or can be dynamically created with Amazon Auto Scaling.

The Jenkins Amazon EC2 Container Service plugin will use this ECS cluster and will create automatically the required Task Definition.

#### Jenkins System Configuration

Navigate to the "Configure System" screen.

In the "Jenkins Location" section, ensure that the "Jenkins URL" is reachable from the the container instances of the Amazon ECS cluster. See the section "Network and firewalls" for more details.

If the global Jenkins URL configuration does not fit your needs (e.g. if your ECS agents must reach Jenkins through some kind of tunnel) you can also override the Jenkins URL in the Advanced Configuration of the ECS cloud.

At the bottom of the screen, click on "Add a new Cloud" and select "Amazon EC2 Container Service Cloud".

#### Amazon EC2 Container Service Cloud

Then enter the configuration details of the Amazon EC2 Container Service Cloud:

-   `Name`: name for your ECS cloud (e.g. `ecs-cloud`)
-   `Amazon ECS Credentials`: Amazon IAM Access Key with privileges to create Task Definitions and Tasks on the desired ECS cluster
-   `ECS Cluster`: desired ECS cluster on which Jenkins will send builds as ECS tasks
-   `ECS Template`: click on "Add" to create the desired ECS template or templates

_Advanced configuration_

-   `Tunnel connection through`: tunnelling options (when Jenkins runs behind a load balancer).
-   `Alternative Jenkins URL`: The URL used as the Jenkins URL within the ECS containers of the configured cloud. Can be used to override the default Jenkins URL from global configuration if needed.

##### ECS Agent Templates

One or several ECS agent templates can be defined for the Amazon EC2 Container Service Cloud. The main reason to create more than one ECS agent template is to use several Docker images to perform build (e.g. java-build-tools, php-build-tools...)

-   `Template name` is used (prefixed with the cloud's name) for the task definition in ECS.
-   `Label`: agent labels used in conjunction with the job level configuration "Restrict where the project can be run / Label expression". ECS agent label could identify the Docker image used for the agent (e.g. `docker` for the jenkinsci/inbound-agent).  Multiple, space delimited labels can be specified(e.g. `java11 alpine`). Label expressions within a job such as `java11 && alpine` or `java11 || alpine` are not currently supported.
-   `Filesystem root`: working directory used by Jenkins (e.g. `/home/jenkins/`).
-   `Memory`: number of MiB of memory reserved for the container. If your container attempts to exceed the memory allocated here, the container is killed.  
-   `CPU units`: number of `cpu units` to reserve for the container. A container instance has 1,024 cpu units for every CPU core.

_Advanced Configuration_
-   `Override entrypoint`: overwritten Docker image entrypoint. Container command can't be overriden as it is used to pass jenkins agent connection parameters.
-   `JVM arguments`: additional arguments for the JVM, such as `-XX:MaxPermSize` or GC options.

#### Network and firewalls

Running the Jenkins master and the ECS container instances in the same Amazon VPC and in the same subnet is the simplest setup and default settings will work out-of-the-box.

_Firewalls_
If you enable network restrictions between the Jenkins master and the ECS cluster container instances,

* Fix the TCP listen port for JNLP agents of the Jenkins master (e.g. `5000`) navigating in the "Manage Jenkins / Configure Global Security" screen
* Allow TCP traffic from the ECS cluster container instances to the Jenkins master on the listen port for JNLP agents (see above) and the HTTP(S) port.

_Network Address Translation and Reverse Proxies_
In case of Network Address Translation rules between the ECS cluster container instances and the Jenkins master, ensure that the JNLP agents will use the proper hostname to connect to the Jenkins master doing on of the following:

* Define the proper hostname of the Jenkins master defining the system property `hudson.TcpSlaveAgentListener.hostName` in the launch command
* Use the advanced configuration option "Tunnel connection through" in the configuration of the Jenkins Amazon EC2 Container Service Cloud (see above).

### IAM Permissions

To work the plugin needs some IAM permissions. Assign a role with those permissions to the instance / container you are running the master on.

Here is an example of a role in CloudFormation, make sure to modify it for your needs.

```yaml
TaskRole:
    Type: AWS::IAM::Role
    Properties:
        RoleName: !Sub ${AWS::StackName}-task-role
        Path: /
        AssumeRolePolicyDocument:
            Version: 2012-10-17
            Statement:
                - Effect: Allow
                  Principal:
                      Service:
                          - ecs-tasks.amazonaws.com
                  Action: sts:AssumeRole
        Policies:
            - PolicyName: !Sub ecs-${AWS::StackName}
              PolicyDocument:
                  Version: "2012-10-17"
                  Statement:
                      - Action:
                            - "ecs:RegisterTaskDefinition"
                            - "ecs:ListClusters"
                            - "ecs:DescribeContainerInstances"
                            - "ecs:ListTaskDefinitions"
                            - "ecs:DescribeTaskDefinition"
                            - "ecs:DeregisterTaskDefinition"
                            - "ecs:ListTagsForResource"
                        Effect: Allow
                        Resource: "*"
                      - Action:
                            - "ecs:ListContainerInstances"
                            - "ecs:DescribeClusters"
                        Effect: Allow
                        Resource:
                            - !Sub "arn:aws:ecs:${AWS::Region}:${AWS::AccountId}:cluster/<clusterName>"
                      - Action:
                            - "ecs:RunTask"
                        Effect: Allow
                        Condition:
                            ArnEquals:
                                ecs:cluster:
                                    - !Sub "arn:aws:ecs:${AWS::Region}:${AWS::AccountId}:cluster/<clusterName>"
                        Resource: !Sub "arn:aws:ecs:${AWS::Region}:${AWS::AccountId}:task-definition/*"
                      - Action:
                            - "ecs:StopTask"
                        Effect: Allow
                        Condition:
                            ArnEquals:
                                ecs:cluster:
                                    - !Sub "arn:aws:ecs:${AWS::Region}:${AWS::AccountId}:cluster/<clusterName>"
                        Resource: !Sub "arn:aws:ecs:*:*:task/*" # "arn:aws:ecs:${AWS::Region}:${AWS::AccountId}:task/*"
                      - Action:
                            - "ecs:DescribeTasks"
                        Effect: Allow
                        Condition:
                            ArnEquals:
                                ecs:cluster:
                                    - !Sub "arn:aws:ecs:${AWS::Region}:${AWS::AccountId}:cluster/<clusterName>"
                        Resource: !Sub "arn:aws:ecs:*:*:task/*" # "arn:aws:ecs:${AWS::Region}:${AWS::AccountId}:task/*"
                      - Action:
                            - "elasticfilesystem:DescribeAccessPoints"
                            - "elasticfilesystem:DescribeFileSystems"
                        Effect: Allow
                        Resource: !Sub "arn:aws:elasticfilesystem:${AWS::Region}:${AWS::AccountId}:file-system/*"
```

### Agent

The Jenkins Amazon EC2 Container Service Cloud can use for the agents all the Docker image designed to act as a Jenkins JNLP agent. Here is a list of compatible Docker images:

-   [jenkins/inbound-agent](https://hub.docker.com/r/jenkins/inbound-agent/)

You can easily extend the images or also build your own.

## Declarative Pipeline

Declarative Pipeline support requires Jenkins 2.66+

Declarative agents can be defined like shown below. You can also reuse pre-configured templates and override certain settings using `inheritFrom` to reference the _Label field_
of the template that you want to use as preconfigured. Only one label is expected to be specified.

When using inheritFrom, the label will not copied. Instead, a new label will be generated based on the following schema {job-name}-{job-run-number}-{5-random-chars} e.g. "pylint-543-b4f42". This guarantees that there will not be conflicts with the parent template or other runs of the same job, as well as making it easier to identify the labels in Jenkins.

If you want to override the label, ensure that you are not going to conflict with other labels configured elsewhere. Templates for dynamic agents exist until the agent dies, meaning other jobs requesting the same label (including dynamic agents on other runs of the same job!) run the chance of provisioning the dynamic agent's ECSTask.

_Note_: You have to configure list of settings to be allowed in the declarative pipeline first (see the Allowed Overrides setting). They are disabled by default for security reasons, to avoid non-privileged users to suddenly be able to change certain settings.

If Jenkins is unexpectedly shut down there is a good chance that ECS Tasks for dynamic agents will not be cleaned up (de-registered) in AWS. This should not cause issues, but may come as a surprise when looking at the console.

## Usage

The ECS agents can be used for any job and any type of job (Freestyle job, Maven job, Workflow job...), you just have to restrict the execution of the jobs on one of the labels used in the ECS Agent Template configuration. You can either restrict the job to run on a specific label only via the UI or directly in the pipeline.

```groovy

pipeline {
  agent none

  stages {
       stage('PublishAndTests') {
          environment {
              STAGE='prod'
          }
          agent {
            label 'build-python36'
          }
      }
      steps {
        sh 'java -version'
      }
    }
  }
```

```groovy
pipeline {
  agent none

  stages {
    stage('Test') {
        agent {
            ecs {
                inheritFrom 'label-of-my-preconfigured-template'
                cpu 2048
                memory 4096
                image '$AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/jenkins/java8:2019.7.29-1'
                logDriver 'fluentd'
                logDriverOptions([[name: 'foo', value:'bar'], [name: 'bar', value: 'foo']])
                portMappings([[containerPort: 22, hostPort: 22, protocol: 'tcp'], [containerPort: 443, hostPort: 443, protocol: 'tcp']])
            }
        }
        steps {
            sh 'echo hello'
        }
    }
  }
}
```

Scripted Pipeline examples

```groovy
def dynamic_label = "${JOB_NAME}_${env.sha}"
ecsTaskTemplate(
    cloud: 'CloudNameAsConfiguredInManageClouds',
    label: dynamic_label,
    name: dynamic_label, // Reusing the label as a name makes sense as long as it's unique
    containerUser: 'ubuntu',
    remoteFSRoot: '/home/ubuntu',
    overrides: [],
    agentContainerName: 'java',    
    taskDefinitionOverride: "arn:aws:redacted:redacted:task-definition/${env.task}"
) {
  node(dynamic_label) {
    stage("I dunno why you say goodbye"){
      sh 'echo hello'
    }
  }
}
```

```groovy
pipeline{
    agent {
        ecs {
          inheritFrom 'ecs_test'
          cpu 1000
        } 
    }
    stages{
        stage("Here goes nothin"){
          sh 'echo hello'
        }
    }
}
```
## FAQ

### My parallel jobs don't start at the same time

Actually, there can be multiple reasons:

-   The plugin creates a new agent only when the stage contains an `agent` [definition](https://jenkins.io/doc/book/pipeline/syntax/#agent). If this is missing, the stage inherits the agent definition from the level above and also re-uses the instance.

-   Also, parallel stages sometimes don't really start at the same time. Especially, when the provided label of the `agent` definition is the same. The reason is that Jenkins tries to guess how many instances are really needed and tells the plugin to start n instances of the agent with label x. This number is likely smaller than the number of parallel stages that you've declared in your Jenkinsfile. Jenkins calls the ECS plugin multiple times to get the total number of agents running.

-   If launching of the agents takes long, and Jenkins calls the plugin in the meantime again to start n instances, the ECS plugin doesn't know if this instances are really needed or just requested because of the slow start. That's why the ECS plugin subtracts the number of launching agents from the number of requested agents (for a specific label). This can mean for parallel stages that some of the agents are launched after the previous bunch of agents becomes online.

There are options that influence how Jenkins spawns new Agents. You can set for example on your master the following to improve the launch times:

```bash
-Dhudson.slaves.NodeProvisioner.initialDelay=0 -Dhudson.slaves.NodeProvisioner.MARGIN=50 -Dhudson.slaves.NodeProvisioner.MARGIN0=0.85
```

## Who runs this & Resources

If you are running a interesting setup or have public posts abour your setups using this plugins, please file a PR to get it added here.

-   Slides: [Run Jenkins as managed product on ECS](https://www.slideshare.net/PhilippGarbe1/run-jenkins-as-managed-product-on-ecs-aws-meetup)
-   [Youtube: Jenkins with Amazon ECS slaves](https://www.youtube.com/watch?v=v0b53cdrujs)
- [AWS Blog - Jenkins on AWS](https://aws.amazon.com/blogs/opensource/why-jenkins-still-continuously-serves-developers/)

## Maintainers

Andreas Sieferlinger ([GitHub](https://github.com/webratz) [Twitter](https://twitter.com/webratz))  
Philipp Garbe ([GitHub](https://github.com/pgarbe), [Twitter](https://twitter.com/pgarbe))  
Marky Jackson ([GitHub](https://github.com/markyjackson-taulia), [Twitter](https://twitter.com/MarkyJackson3))  
Stephen Erickson ([GitHub](https://github.com/stericson))

## Developing

### Building the Plugin

```bash
  java -version # Need Java 1.8, earlier versions are unsupported for build
  mvn -version # Need a modern maven version; maven 3.2.5 and 3.5.0 are known to work
  mvn clean install
```

### Running locally

To run locally, execute the following command and open the browser [http://localhost:8080/jenkins/](http://localhost:8080/jenkins/)

```bash
  mvn -e hpi:run
```

### Debugging the plugin in an editor

#### IntelliJ IDEA

In the Maven dialog right click `hpi:run` and select `Debug`. 
The IDE will stop at any breakpoints you have set inside the plugin.

#### Other

the

```java

    @Rule
    public JenkinsRule j = new JenkinsRule();
```

Will actually invoke code that will bootstrap a local installation of `jenkins.war`. This will allow you to debug with with breakpoints and such. However, to do it
you will need to set some system properties or be aware how it tries to auto-configure. It will attempt to look for a `.jenkins` directory recursively with an already exploded war,
So, theoretically you explode it, and git ignore it, right in this space. Alternatively, you can set a System property:
`-Djth.jenkins-war.path=${PATH}/jenkins.war`

Make sure to include this rule in any tests that touch Jenkins specific resources like: `Jenkins.instance()`

### Releasing the Plugin

```bash
 mvn clean release:prepare release:perform
```

#### further checks to aid with development

Check for additional or forgotten dependencies:

```bash
mvn dependency:analyze
```

Check if javadoc works fine (usually only executed on release)

```bash
mvn org.apache.maven.plugins:maven-javadoc-plugin:2.10.4:jar
```
