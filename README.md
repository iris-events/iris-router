# Websocket router

This project uses Quarkus, the Supersonic Subatomic Java Framework.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```
./mvnw quarkus:dev
```


## Running in Kubernetes on AWS

### Prerequisites

In order to run webhooks service in a K8s cluster the following prerequisites must be met:
1. Configmap with correct environment specific properties set:<br />
   ```properties
   RABBIT_HOST
   RABBIT_USERNAME
   ```
2. Secrets map with correct environment specific property values:
   ```properties
   RABBIT_PASSWORD
   ```
