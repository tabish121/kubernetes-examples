/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.example;

import org.apache.qpid.proton.amqp.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class WeatherFrontend extends AbstractVerticle {

    private static Logger LOG = LoggerFactory.getLogger(WeatherFrontend.class);

    public static final Symbol QUEUE_CAPABILITY = Symbol.valueOf("queue");

    private WebClient weatherAPIClient;
    private MessagingClient messagingClient;

    private String messagingHost;
    private int messagingPort;
    private String messagingUsername;
    private String messagingPassword;
    private String httpHost;
    private int httpPort;

    private JsonObject latestWeatherUpdate;

    @Override
    public void start(Future<Void> start) throws Exception {
        initializeServiceConfiguration();

        weatherAPIClient = createAndConfigureWebClient();
        messagingClient = createAndConfigureMessagingClient();

        // Routes for this service to check status and monitor health
        Router router = Router.router(vertx);
        router.route("/").handler(this::serviceStatusPage);
        router.route("/health").handler(this::checkHealth);
        router.route("/ready").handler(this::checkReady);

        vertx.createHttpServer().requestHandler(router).listen(
            httpPort, httpHost, result -> {
                if (result.succeeded()) {
                    start.complete();
                } else {
                    start.fail(result.cause());
                }
            });

        LOG.info("Weather API service started on {}:{}", httpHost, httpPort);
    }

    private WebClient createAndConfigureWebClient() {
        WebClientOptions options = new WebClientOptions();

        options.setUserAgent("WeatherProvider/1.0.0");
        options.setKeepAlive(false);

        return WebClient.create(vertx, options);
    }

    private MessagingClient createAndConfigureMessagingClient() {
        MessagingClient messagingClient = new MessagingClient(
            getVertx(), messagingHost, messagingPort, "weather.current.zipcode.22314", "weather");

        messagingClient.connect();

        return messagingClient;
    }

    private void serviceStatusPage(RoutingContext routingContext) {
        HttpServerResponse response = routingContext.response();
        response.putHeader("content-type", "text/html");

        if (latestWeatherUpdate != null) {
            response.end("<h1>Weather API service running</h1>\n" + latestWeatherUpdate.encodePrettily());
        } else {
            response.end("<h1>Weather API service running but no data available yet.</h1>\n");
        }
    }

    private void checkHealth(RoutingContext routingContext) {
        HttpServerResponse response = routingContext.response();
        final int statusCode;
        final String statusMessage;

        // We don't currently have a measure of our health
        statusCode = 200;
        statusMessage = "Weather API reports healthy";

        response.setStatusCode(statusCode)
                .putHeader("content-type", "text/html")
                .end("<h1>" + statusMessage + "</h1>\n");
    }

    private void checkReady(RoutingContext routingContext) {
        HttpServerResponse response = routingContext.response();
        final int statusCode;
        final String statusMessage;

        // We don't currently have a measure of our health
        statusCode = 200;
        statusMessage = "Weather API reports ready";
        response.setStatusCode(statusCode)
                .putHeader("content-type", "text/html")
                .end("<h1>" + statusMessage + "</h1>\n");
    }

    private void initializeServiceConfiguration() {
        messagingHost = System.getenv("MESSAGING_SERVICE_HOST");
        String messagingPort = System.getenv("MESSAGING_SERVICE_PORT");
        messagingUsername = System.getenv("MESSAGING_SERVICE_USER");
        messagingPassword = System.getenv("MESSAGING_SERVICE_PASSWORD");
        httpHost = System.getenv("HTTP_HOST");
        String httpPort = System.getenv("HTTP_PORT");

        if (messagingHost == null) messagingHost = "localhost";
        if (messagingPort == null) {
            this.messagingPort = 5672;
        } else {
            this.messagingPort = Integer.parseInt(messagingPort);
        }
        if (messagingUsername == null) messagingUsername = "example";
        if (messagingPassword == null) messagingPassword = "example";

        if (httpHost == null) {
            httpHost = config().getString("http.host", "0.0.0.0");
        }

        if (httpPort == null) {
            this.httpPort = config().getInteger("http.port", 8080);
        } else {
            this.httpPort = Integer.parseInt(httpPort);
        }
    }

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(WeatherFrontend.class.getName());
    }
}
