/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.gradle.utils;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class RetryUtils {

    public static <T>  RetryBuilder<T> retry(Supplier<T> action) {
        return new RetryBuilder<>(action);
    }

    public static class RetryBuilder<T> {

        private final Supplier<T> action;
        private Optional<RetryScheduler> scheduler = Optional.empty();
        private Optional<Consumer<Exception>> retryErrorConsumer = Optional.empty();
        private Optional<Integer> maxAttempt = Optional.empty();
        private Optional<Long> initialDelay = Optional.empty();

        private RetryBuilder(Supplier<T> action) {
            this.action = action;
        }

        public RetryBuilder<T> scheduler(RetryScheduler scheduler) {
            this.scheduler = Optional.ofNullable(scheduler);
            return this;
        }

        public RetryBuilder<T> exponentialBackoff(long baseTime, long cap) {
            this.scheduler = Optional.of(new ExponentialBackoff(baseTime, cap));
            return this;
        }

        public RetryBuilder<T> initialDelay(long initialDelay) {
            this.initialDelay = Optional.of(initialDelay);
            return this;
        }

        public RetryBuilder<T> onRetryError(Consumer<Exception> onError) {
            this.retryErrorConsumer = Optional.ofNullable(onError);
            return this;
        }

        public RetryBuilder<T> maxAttempt(int maxAttempt) {
            this.maxAttempt = Optional.of(maxAttempt);
            return this;
        }

        private T execute(int attempts) {
            if (attempts == 0) {
                this.initialDelay.ifPresent(delay -> {
                    try {
                        Thread.sleep(delay);
                    }
                    catch (InterruptedException e) {
                        //may be trying to shut-down. End early.
                    }
                });
            }

            try {
                return action.get();
            } catch (Exception e) {
                maxAttempt
                        .filter(maxAttempt -> attempts >= maxAttempt - 1)
                        .ifPresent( it -> { throw e; } );
                retryErrorConsumer.ifPresent(onError -> onError.accept(e));
                scheduler.ifPresent(scheduler -> {
                    try {
                        Thread.sleep(scheduler.deferTime(attempts));
                    } catch (InterruptedException interruptedException) {
                        throw new RetryException("Error while waiting during retry "+ attempts, interruptedException);
                    }
                });
                return execute(attempts + 1);
            }
        }

        public T execute() {
            return execute(0);
        }

    }

    public interface RetryScheduler {
        long deferTime(int count);
    }

    public static class ExponentialBackoff implements RetryScheduler {
        private final long base;
        private final long cap;

        public ExponentialBackoff(long base, long cap) {
            this.base = base;
            this.cap = cap;
        }

        @Override
        public long deferTime(int count) {
            final long expWait = ((long) Math.pow(2, count) * base);
            return expWait <= 0 ? cap : Math.min(cap, expWait);
        }
    }

    public static class RetryException extends RuntimeException {
        public RetryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
