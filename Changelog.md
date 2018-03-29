# Changelog

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
