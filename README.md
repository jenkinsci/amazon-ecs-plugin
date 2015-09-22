# Amazon EC2 Container Service Plugin for Jenkins

## About

This jenkins plugin do use [Amazon EC2 Container Service](http://docs.aws.amazon.com/AmazonECS/latest/developerguide/Welcome.html) to host jobs execution inside docker containers.

## Installation

After installation the plugin do offer a new option in JENKINS/configure to setup a Cloud provider on Amazon ECS.

After configuring AWS Credentials, plugin will let administrator select the ECS cluster to run jenkins builds.

Last section of the plugin configuration is used to declare docker container slaves templates, and the label used by jobs to select them. We recommend to use `jenkinsci/jnlp-slave` as the basis for custom docker images, as this one do handle the slave setup.
