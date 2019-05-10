# Weather REST Frontend Example

This example creates a Vert.x based application that will pull provide a REST API for querying for weather conditions from a backend service.

## Build and publish the Docker image

You need to build the image and then publish it to docker.io under your account, the below script shows how this is done.

    docker build -t <your-docker-id>/weather-frontend-example .
    docker login
    docker push <your-docker-id>/weather-frontend-example

