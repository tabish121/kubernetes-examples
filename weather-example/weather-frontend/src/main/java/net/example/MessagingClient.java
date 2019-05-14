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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonReceiver;

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

    private ProtonConnection connection;

    private Map<String, Consumer<String>> subscriptions = new HashMap<>();

    // Reconnection delay tracking
    private int nextDelay;

    public MessagingClient(Vertx vertx, String hostname, int port) {
        this.vertx = vertx;
        this.client = ProtonClient.create(vertx);
        this.hostname = hostname;
        this.port = port;
    }

    // Client API

    public void connect() {
        tryConnect();
    }

    public void subscribe(String address, Consumer<String> eventHandler) {
        subscriptions.put(address, eventHandler);

        if (connection != null) {
            ProtonReceiver receiver = connection.createReceiver(address);
            ((Source) receiver.getSource()).setCapabilities(QUEUE_CAPABILITY);

            receiver.handler((delivery, message) -> {
                if (message.getBody() instanceof AmqpValue) {
                    try {
                        String payload = (String) ((AmqpValue) message.getBody()).getValue();
                        eventHandler.accept(payload);
                    } catch (Throwable t) {}
                }
            }).open().openHandler(opened -> {
                opened.result().setPrefetch(1);
            });
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
        for (Entry<String, Consumer<String>> subscription : subscriptions.entrySet()) {
            subscribe(subscription.getKey(), subscription.getValue());
        }
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
