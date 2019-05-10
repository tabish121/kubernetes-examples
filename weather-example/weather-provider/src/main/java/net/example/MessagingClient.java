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

import static io.vertx.proton.ProtonHelper.message;

import java.time.Instant;
import java.util.LinkedHashMap;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonSender;

public class MessagingClient {

    private static Logger LOG = LoggerFactory.getLogger(MessagingClient.class);

    public static final Symbol QUEUE_CAPABILITY = Symbol.valueOf("queue");
    public static final Symbol TOPIC_CAPABILITY = Symbol.valueOf("topic");

    private final int MAX_RECONNECT_DELAY = 30_000;
    private final int INITIAL_RECONNECT_DELAY = 10;
    private final int CONNECT_ATTEMPT_BACKOFF = 2;

    private final Vertx vertx;
    private final ProtonClient client;

    private final String hostname;
    private final int port;
    private final String address;
    private final String weatherKey;

    private ProtonConnection connection;

    // Reconnection delay tracking
    private int nextDelay;

    public MessagingClient(Vertx vertx, String hostname, int port, String address, String key) {
        this.vertx = vertx;
        this.client = ProtonClient.create(vertx);
        this.hostname = hostname;
        this.port = port;
        this.address = address;
        this.weatherKey = key;
    }

    // Client API

    public void connect() {
        tryConnect();
    }

    public void sendMessage(JsonObject weatherData, String dataContext) {
        if (connection != null) {
            ProtonSender sender = connection.createSender(address);
            ((Target) sender.getTarget()).setCapabilities(QUEUE_CAPABILITY);
            sender.setAutoSettle(true);
            sender.setAutoDrained(true);

            sender.open();
            try {
                if (!sender.sendQueueFull()) {
                    Message message = message(weatherData.encode());
                    ApplicationProperties applicationProperties = new ApplicationProperties(new LinkedHashMap<>());
                    applicationProperties.getValue().put(weatherKey, dataContext);
                    message.setApplicationProperties(applicationProperties);
                    sender.send(message);
                }
            } finally {
                sender.close();
            }
        } else {
            LOG.info("Connection to broker is down, no data published at this time");
        }
    }

    public void shutdown() {
        if (connection != null) {
            connection.disconnectHandler(null);
            connection.closeHandler(ignored -> {
                try {
                    connection.disconnect();
                } finally {
                    vertx.close();
                }
            });

            connection.close();
            connection = null;
        }
    }

    //----- Client Internals

    private void tryConnect() {
        LOG.debug("Attempting to connect to peer: {}:{}", hostname, port);

        client.connect(hostname, port, result -> {
            if (!result.succeeded()) {
                LOG.debug("Connect failed: " + result.cause());
                handleConnectionFailure(false);
                return;
            }

            connection = result.result();
            connection.openHandler(x -> {
                connected();
            }).closeHandler(x -> {
                handleConnectionFailure(true);
            }).disconnectHandler(x -> {
                handleConnectionFailure(false);
            }).open();
        });
    }

    private void handleConnectionFailure(boolean remoteClose) {
        try {
            if (connection != null) {
                connection.closeHandler(null);
                connection.disconnectHandler(null);

                // Remotely closed connection require local close to finish the cycle.
                if (remoteClose) {
                    connection.close();
                    connection.disconnect();
                }

                connection = null;
            }
        } finally {
            scheduleReconnect();
        }
    }

    private void connected() {
        nextDelay = 0;
    }

    private int nextReconnectDelay() {
        int result = nextDelay;

        if (nextDelay == 0) {
            nextDelay = INITIAL_RECONNECT_DELAY;
        } else {
            nextDelay = Math.min(nextDelay * CONNECT_ATTEMPT_BACKOFF, MAX_RECONNECT_DELAY);
        }

        return result;
    }

    private void scheduleReconnect() {
        int delay = nextReconnectDelay();

        if (delay <= 0) {
            Vertx.currentContext().runOnContext(x -> {
                tryConnect();
            });
        } else {
            LOG.trace("Scheduling connect attempt in {} ms at {}", delay, Instant.now().plusMillis(delay));
            vertx.setTimer(delay, x -> {
                tryConnect();
            });
        }
    }
}
