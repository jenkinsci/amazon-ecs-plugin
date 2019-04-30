# Changelog

## vNext

## 1.20
- Feature - Declarative agent configuration #79
- BUG - Fixed typo in configuration (slaveTimoutInSeconds) wasn't saved properly

## 1.19
- BUG - [JENKINS-54898] ECS Plugin 1.18 cannot launch agents #77
- BUG - [JENKINS-55078] No longer able to start ECS agents #78

BREAKING: Fixed typo in config: `slaveTimoutInSeconds` is now `slaveTimeoutInSeconds`


## 1.18
- Feature - Introduce ECSLauncher #72
- Feature - [JENKINS-54380] Private repository authentication #74
- Feature - [JENKINS-52032] Replace slave occurrence by agent in the Amazon ECS plugin
- Feature - New setting for retention timeout
- Feature - [JENKINS-54473] Rename to agents where possible
- BUG - [JENKINS-54473] Add support for Windows containers NetworkMode, which must be null. #76

## 1.17
- Feature - Enabling use of awsvpc network mode with the EC2 launch type #68
- Feature - Use [OnceRetentionStrategy](https://javadoc.jenkins.io/plugin/durable-task/org/jenkinsci/plugins/durabletask/executors/OnceRetentionStrategy.html) #71
- Bug - [[JENKINS-51775](https://issues.jenkins-ci.org/browse/)] New ECS task definition created for each instance of an Agent requested
- BUG - Agent configuration page crashes #70

## 1.16
- Feature - Added executionRole field for fargate support #62
- BUG - JENKINS-37597 Remove Tasks which are not responding #63

## 1.15
- Feature - Allow using externally-managed task definitions #50
- Feature - [JENKINS-49020] Add support for Fargate #51
- BUG - Allow multiple slaves to run with the same template #48
- BUG - Fix NPE if templates is null #52
- BUG - Fixing null launchType usage #61

## 1.14
- BUG - Treat empty username as null #59

## 1.13
- BUG - [JENKINS-46581] DNS Search Domains not honored in Amazon ECS Plugin #42
- Feature - Adding Port Mapping options for task templates #44
- Feature - Add container user #49

## 1.12
- Bug - [JENKINS-50381] Fix label string passed to the ECSSlave constructor #55
- Bug - [JENKINS-39220] list all available ECS cluster (instead of first 100) #46

## 1.11
- JENKINS-41993: Amazon ECS plugin fails to register task definition v 1.10
- PR #40: Add DnsSearchDomains option

## 1.10
- Task definitions are now created on demand and no longer on save of Jenkins configuration page

## 1.9
- JENKINS-40300: Add MemoryReservation to set soft memory limits on the containers
- PR #39: Allow Specification of Task Role ARN for ECS Slave Templates

## 1.8
- Added taskRoleArn configuration option
- minor typos fixed

## 1.7
- Added slave and task removal through RetentionStrategy (#35)
- Added surveillance of ECS tasks for dead instances (attention: This requires ecs:DescribeTasks permission in AWS)
- Slave timeout is no configurable in UI

## 1.6
- Now a name is required for each template and used to derive the ECS task definition name.
- Slave'name and secret are not availabale during slave startup in the environment variables SLAVE_NODE_NAME and SLAVE_NODE_SECRET.
- ECS plugin no longer tries to provision slaves when requests label is empty.

## 1.5
- [JENKINS-36857] Sleep 1000 ms between 2 interactions with AWS ECS API

## 1.1
- Support implicit AWS credentials (`~/.aws/credentials`)
- Only create a taskDefinition when template is saved

## 1.0
Initial release
