.PHONY: help deploy-master docker deploy-ecr

stackName = jenkins
region = eu-west-1
accountid = $(shell aws sts get-caller-identity --query Account --output text)
servicename = $(shell cat parameters.properties|grep ServiceName)

.DEFAULT: help
help:
	@echo "make deploy-master"
	@echo "       deploys Jenkins Master on Fargate"

debug-master:
	aws --version
	aws sts get-caller-identity
	aws cloudformation describe-stack-events --stack-name $(stackName)-master

deploy-ecr:
	aws cloudformation deploy \
		--template-file cloudformation-ecr.yaml \
		--stack-name $(stackName)-ecr \
		--parameter-overrides $(shell cat parameters.properties|grep ServiceName) \
		--no-fail-on-empty-changeset

	@aws cloudformation describe-stacks \
		--stack-name $(stackName)-ecr \
		--output text \
		--query Stacks[0].Outputs[*].OutputValue

docker:
	docker build -t $(shell aws ssm get-parameters --region eu-west-1 --names /jenkins/docker-uri --query Parameters[].Value --output text):latest docker
	aws ecr get-login-password --region $(region) | docker login --username AWS --password-stdin $(accountid).dkr.ecr.$(region).amazonaws.com
	docker push $(shell aws ssm get-parameters --region eu-west-1 --names /jenkins/docker-uri --query Parameters[].Value --output text):latest

deploy-master:
	aws cloudformation deploy \
		--template-file cloudformation-master.yaml \
		--stack-name $(stackName)-master \
		--parameter-overrides $(shell cat parameters.properties) \
		--capabilities CAPABILITY_NAMED_IAM CAPABILITY_IAM \
		--no-fail-on-empty-changeset

	@aws cloudformation describe-stacks \
		--stack-name $(stackName)-master \
		--output text \
		--query Stacks[0].Outputs[*].OutputValue
