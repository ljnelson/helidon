/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.messaging.connectors.jms;

import java.util.concurrent.Executor;

import io.helidon.messaging.MessagingException;

import jakarta.jms.JMSException;

/**
 * A JMS Text message representation.
 */
public class JmsTextMessage extends AbstractJmsMessage<String> {

    private final jakarta.jms.TextMessage msg;

    JmsTextMessage(jakarta.jms.TextMessage msg, Executor executor, SessionMetadata sharedSessionEntry) {
        super(executor, sharedSessionEntry);
        this.msg = msg;
    }

    @Override
    public jakarta.jms.TextMessage getJmsMessage() {
        return msg;
    }

    @Override
    public String getPayload() {
        try {
            return msg.getText();
        } catch (JMSException e) {
            throw new MessagingException("Error when retrieving payload of JMS text message", e);
        }
    }
}
