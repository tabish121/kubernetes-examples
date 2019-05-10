# Weather Provider Example with Kubernetes

This example demonstrates how to create a simple Weather data collection application and deploy it into [Kubernetes](https://kubernetes.io/) along with an [ActiveMQ Artemis](https://activemq.apache.org/artemis/index.html) broker.  The example leverages [Vert.x](https://vertx.io/) and the [AMQP 1.0](http://www.amqp.org/) messaging protocol to send the data to the Artemis broker.

## Example Overview

The example can be broken down into three basic components:

* An AMQP 1.0 message broker, ActiveMQ Artemis which will store the latest weather data in an Last Value Queue (LVQ).

* A weather data collection application which reads current conditions data from an external service on a periodic basis and sends it the the message broker for distribution to interested clients.

* A front end application that provides a simple REST API to subscribe to and fetch the current weather conditions.

## Steps

1. [Install and run Minikube in your
   environment](https://kubernetes.io/docs/setup/minikube/)

1. Configure your shell to use the Minikube Docker instance:

   ```bash
   $ eval $(minikube docker-env)
   $ echo $DOCKER_HOST
   tcp://192.168.39.67:2376
   ```

1. Create a broker deployment and expose it as a service:

   ```bash
   $ kubectl run broker --image docker.io/ssorj/activemq-artemis
   deployment.apps/broker created
   $ kubectl expose deployment/broker --port 5672
   service/broker exposed
   ```
2. Create the Secret for the Weather Service Provider

   For this step you will need to create an account on OpenWeatherMap.org and create an API key for the provider to use when fetching the weather data.

   ```
   cd ../weather-provider
   $ sed -i 's/{{APPID}}/<your-api-key/g' ./kubernetes/weatherProviderSecret.yaml
   $ kubectl apply -f ./kubernetes/weatherProviderSecret.yaml
   ```

3. Deploy the Weather Provider Service and start it

   ```
   $ kubectl apply -f ./kubernetes/weatherProviderDeployment.yaml
   $ kubectl apply -f ./kubernetes/weatherProviderService.yaml
   ```

## See Also

* [Getting started with Minikube](https://kubernetes.io/docs/tutorials/hello-minikube/)
* [Apache ActiveMQ Artemis](https://activemq.apache.org/artemis/)
* [Artemis container image](https://cloud.docker.com/u/ssorj/repository/docker/ssorj/activemq-artemis)
* [AMQP v1.0 Protocol](https://www.amqp.org/)
* [Eclipse Vert.x](https://vertx.io/)
* [Open Weather Map](https://openweathermap.org/)