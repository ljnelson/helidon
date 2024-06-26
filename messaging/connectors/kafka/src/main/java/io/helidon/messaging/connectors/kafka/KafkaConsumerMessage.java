/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.kafka;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;

/**
 * Kafka specific MP messaging message.
 *
 * @param <K> kafka record key type
 * @param <V> kafka record value type
 */
class KafkaConsumerMessage<K, V> implements KafkaMessage<K, V> {

    private final CompletableFuture<Void> ack;
    private final NackHandler<K, V> nack;
    private final long millisWaitingTimeout;
    private final AtomicBoolean acked = new AtomicBoolean();
    private final ConsumerRecord<K, V> consumerRecord;

    /**
     * Kafka specific MP messaging message.
     *
     * @param consumerRecord       obtained from Kafka topic
     * @param ack                  it will complete when Kafka commit is done.
     * @param millisWaitingTimeout this is the time in milliseconds that the ack will be waiting
     *                             the commit in Kafka. Applies only if autoCommit is false.
     */
    KafkaConsumerMessage(ConsumerRecord<K, V> consumerRecord,
                         CompletableFuture<Void> ack,
                         NackHandler<K, V> nack,
                         long millisWaitingTimeout) {
        Objects.requireNonNull(consumerRecord);
        this.consumerRecord = consumerRecord;
        this.ack = ack;
        this.nack = nack;
        this.millisWaitingTimeout = millisWaitingTimeout;
    }

    @Override
    public Optional<String> getTopic() {
        return getConsumerRecord().map(ConsumerRecord::topic);
    }

    @Override
    public Optional<Integer> getPartition() {
        return getConsumerRecord().map(ConsumerRecord::partition);
    }

    @Override
    public Optional<Long> getOffset() {
        return getConsumerRecord().map(ConsumerRecord::offset);
    }

    @Override
    public Headers getHeaders() {
        return this.consumerRecord.headers();
    }

    @Override
    public Optional<ConsumerRecord<K, V>> getConsumerRecord() {
        return Optional.of(this.consumerRecord);
    }

    @Override
    public Optional<K> getKey() {
        return getConsumerRecord().map(ConsumerRecord::key);
    }

    @Override
    public V getPayload() {
        return this.consumerRecord.value();
    }

    @Override
    public CompletionStage<Void> ack() {
        return getAck().get();
    }

    @Override
    public Function<Throwable, CompletionStage<Void>> getNack() {
        return nack.getNack(this);
    }

    @Override
    public Supplier<CompletionStage<Void>> getAck() {
        return () -> {
            acked.getAndSet(true);
            if (millisWaitingTimeout == Long.MAX_VALUE) {
                return ack;
            }
            return ack.orTimeout(millisWaitingTimeout, TimeUnit.MILLISECONDS);
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C> C unwrap(Class<C> unwrapType) {
        if (consumerRecord.getClass().isAssignableFrom(unwrapType)) {
            return (C) consumerRecord;
        } else {
            throw new IllegalArgumentException("Can't unwrap "
                    + consumerRecord.getClass().getName()
                    + " to "
                    + unwrapType.getName());
        }
    }

    boolean isAck() {
        return acked.get();
    }

    @Override
    public String toString() {
        return "KafkaConsumerMessage [consumerRecord=" + consumerRecord + ", acked=" + acked + "]";
    }

    CompletableFuture<Void> kafkaCommit() {
        return ack;
    }
}
