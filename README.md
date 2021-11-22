# Webhooks infra

This project uses Quarkus, the Supersonic Subatomic Java Framework.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```
./mvnw quarkus:dev
```

## Packaging and running the application

The application can be packaged using `./mvnw package`.
It produces the `webhooks-1.0.0-SNAPSHOT-runner.jar` file in the `/target` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/lib` directory.

The application is now runnable using `java -jar target/webhooks-1.0.0-SNAPSHOT-runner.jar`.

## Creating a native executable

You can create a native executable using: `./mvnw package -Pnative`.

Or, if you don't have GraalVM installed, you can run the native executable build in a container using: `./mvnw package -Pnative -Dquarkus.native.container-build=true`.

You can then execute your native executable with: `./target/webhooks-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/building-native-image.

## Running in Kubernetes on AWS

### Prerequisites

In order to run webhooks service in a K8s cluster the following prerequisites must be met:
1. Configmap with correct environment specific properties set:<br />
   ```properties
   quarkus.kubernetes.annotations."iam.amazonaws.com/role"
   quarkus.security.users.embedded.roles.test
   RABBIT_HOST
   RABBIT_USERNAME
   ```
   Property file is applied to the currently selected K8s namespace with the following `kubectl` command:
   ```
   kubectl create configmap webhooks --from-file config/dev-webhooks.properties
   ```
2. Secrets map with correct environment specific property values:<br />
   Secrets file with development namespace settings is available within resources folder of the project
   and is applied to the currently selected K8s namespace with the following `kubectl` command:
   ```
   kubectl apply -f src/main/resources/webhooks-secrets.yml