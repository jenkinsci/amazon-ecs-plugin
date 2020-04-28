# Jenkins AWS Fargate Example

This is an example to show how all the components play together. It can be used as inspiration for building setups. But there are several issues as outlined below:

**Work in progess!** This template is not yet optmized or fully usable. It is intended for testing and exploring only

Things missing:

-   persistence for master (EFS, with ne Fargate 1.4 platform version)(no cloudformation support yet)
-   proper upgrade of versions in master
-   network security; currently agent and web connections are allowed from everywhere
-   non hardcoded security credentials - currently the default passwords are used

## Usage

### pre-requisites

You will need:

-   AWS account (with admin access to it) to deploy the templates
-   `aws` cli installed
-   docker
-   `route53` Zone in your account
-   `ACM` certificate valid for the hostname you want to use

#### configuration

-   update the `parameters.properties` file. This contains infos needed for the cloudformation stacks

### deploy

**Important** This setup is not meant to be a production ready configuration. Review all the current mentioned issues and the cloudformation template itself before deploying

The commands can be combined, but are listed separate for easier debugging

```bash
make deploy-ecr
make docker
deploy-master
```

### login

Login at the URL displayed from the make commands

**User**: `ecsuser`
**Password**: `passw0rd1337`
