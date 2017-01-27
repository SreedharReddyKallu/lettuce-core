/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lambdaworks.redis;

import com.lambdaworks.redis.codec.ByteArrayCodec;
import org.openjdk.jmh.annotations.*;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.lambdaworks.redis.api.StatefulRedisConnection;

/**
 * @author Mark Paluch
 */
@State(Scope.Benchmark)
public class RedisClientBenchmark {

    private final static int BATCH_SIZE = 20;
    private final static byte[] KEY = "benchmark".getBytes();

    private RedisClient redisClient;
    private StatefulRedisConnection<byte[], byte[]> connection;
    private RedisFuture commands[];
    private Mono monos[];

    @Setup
    public void setup() {

        redisClient = RedisClient.create(RedisURI.create(TestSettings.host(), TestSettings.port()));
        connection = redisClient.connect(ByteArrayCodec.INSTANCE);
        commands = new RedisFuture[BATCH_SIZE];
        monos = new Mono[BATCH_SIZE];
    }

    @TearDown
    public void tearDown() {

        connection.close();
        redisClient.shutdown();
    }

    @Benchmark
    public void asyncSet() {
        connection.async().set(KEY, KEY).toCompletableFuture().join();
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void asyncSetBatch() throws Exception {

        for (int i = 0; i < BATCH_SIZE; i++) {
            commands[i] = connection.async().set(KEY, KEY);
        }

        for (int i = 0; i < BATCH_SIZE; i++) {
            commands[i].get();
        }
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void asyncSetBatchFlush() throws Exception {

        connection.setAutoFlushCommands(false);

        for (int i = 0; i < BATCH_SIZE; i++) {
            commands[i] = connection.async().set(KEY, KEY);
        }

        connection.flushCommands();
        connection.setAutoFlushCommands(true);

        for (int i = 0; i < BATCH_SIZE; i++) {
            commands[i].get();
        }
    }

    @Benchmark
    public void syncSet() {
        connection.sync().set(KEY, KEY);
    }

    @Benchmark
    public void reactiveSet() {
        connection.reactive().set(KEY, KEY).block();
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void reactiveSetBatch() throws Exception {

        for (int i = 0; i < BATCH_SIZE; i++) {
            monos[i] = connection.reactive().set(KEY, KEY);
        }

        Flux.merge(monos).blockLast();
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void reactiveSetBatchFlush() throws Exception {

        connection.setAutoFlushCommands(false);

        for (int i = 0; i < BATCH_SIZE; i++) {
            monos[i] = connection.reactive().set(KEY, KEY);
        }

        Flux.merge(monos).doOnSubscribe(subscription -> {

            connection.flushCommands();
            connection.setAutoFlushCommands(true);

        }).blockLast();
    }
}
