# Changelog

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
