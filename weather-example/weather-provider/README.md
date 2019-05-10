# Weather Provider Service Example

This example creates a Vert.x based application that will pull weather data from OpenWeatherMap.io and publish it to an ActiveMQ Artemis broker.

## Build and publish the Docker image

You need to build the image and then publish it to docker.io under your account, the below script shows how this is done.

    docker build -t <your-docker-id>/weather-provider-example .
    docker login
    docker push <your-docker-id>/weather-provider-example

