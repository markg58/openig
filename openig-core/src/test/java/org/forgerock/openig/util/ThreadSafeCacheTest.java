/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.util.time.Duration.duration;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.Duration;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ThreadSafeCacheTest {

    public static final Duration DEFAULT_CACHE_TIMEOUT = duration("30 seconds");
    public static final int NUMBER_OF_ENTRIES = 10;
    public static final int NUMBER_OF_THREADS = 20;
    public static final int INVOCATION_COUNT = 10000;

    private ThreadSafeCache<Integer, Integer> cache;

    @Mock
    private ScheduledExecutorService executorService;

    @Captor
    private ArgumentCaptor<Long> delayCaptor;

    @Captor
    private ArgumentCaptor<TimeUnit> timeUnitCaptor;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        cache = new ThreadSafeCache<>(executorService);
        cache.setDefaultTimeout(DEFAULT_CACHE_TIMEOUT);
    }

    @Test
    public void shouldNotComputeValueMoreThanTenTimes() throws Exception {

        // This "stress" test ensure that the cache only compute each entry once
        // We register a lot of cache calls in an executor service to ensure that multiple Threads are used to
        // perform the operations.
        // Each operation generates a random number between 0 and 10 (that are the possible "slots" in the cache) and
        // tries to get the cached value associated to this slot. Each operation waits for a little time to ensure
        // some overlap between tasks.
        // In order to test that the cached values are created only once, we track the number of Callable invocation.
        // At the end of the test, the number of time we created a value should be equal to the number of slots in
        // the cache.

        final Random random = new Random();
        final AtomicInteger count = new AtomicInteger();

        ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

        // Register n cache call...
        for (int i = 0; i < INVOCATION_COUNT; i++) {
            executorService.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    // Randomly select a cache key
                    final int value = Math.abs(random.nextInt() % NUMBER_OF_ENTRIES);

                    // Get the key value
                    cache.getValue(value, new Callable<Integer>() {
                        @Override
                        public Integer call() throws Exception {
                            // Wait for a little time
                            Thread.sleep(value * 5);
                            // Increment the counter of real value creation calls
                            count.incrementAndGet();
                            return value;
                        }
                    });
                    return null;
                }
            });

        }

        // Stop the executor and wait for termination
        executorService.shutdown();
        executorService.awaitTermination(20L, TimeUnit.SECONDS);

        // Each entry should only be computed once
        assertThat(count.get()).isEqualTo(NUMBER_OF_ENTRIES);
    }

    @Test
    public void shouldRegisterAnExpirationCallbackWithAppropriateDuration() throws Exception {

        cache.getValue(42, getCallable());

        verify(executorService).schedule(any(Callable.class),
                                         delayCaptor.capture(),
                                         timeUnitCaptor.capture());
        assertThat(delayCaptor.getValue()).isEqualTo(DEFAULT_CACHE_TIMEOUT.getValue());
        assertThat(timeUnitCaptor.getValue()).isEqualTo(DEFAULT_CACHE_TIMEOUT.getUnit());
    }

    @Test
    public void shouldOverrideDefaultTimeout() throws Exception {
        final Duration lowerDuration = duration("10 seconds");

        cache.getValue(42, getCallable(), new AsyncFunction<Integer, Duration, Exception>() {

            @Override
            public Promise<Duration, Exception> apply(Integer value) throws Exception {
                return newResultPromise(lowerDuration);
            }
        });

        verify(executorService).schedule(any(Callable.class),
                                         delayCaptor.capture(),
                                         timeUnitCaptor.capture());
        assertThat(delayCaptor.getValue()).isEqualTo(lowerDuration.getValue());
        assertThat(timeUnitCaptor.getValue()).isEqualTo(lowerDuration.getUnit());
    }

    @Test
    public void shouldNotCacheTheValueWhenTimeoutIsZero() throws Exception {
        cache.getValue(42, getCallable(), new AsyncFunction<Integer, Duration, Exception>() {

            @Override
            public Promise<Duration, Exception> apply(Integer value) throws Exception {
                return newResultPromise(duration("0 second"));
            }
        });

        verify(executorService).submit(any(Callable.class));
    }

    @Test
    public void shouldNotScheduleExpirationWhenTimeoutIsUnlimited() throws Exception {
        cache.getValue(42, getCallable(), new AsyncFunction<Integer, Duration, Exception>() {

            @Override
            public Promise<Duration, Exception> apply(Integer value) throws Exception {
                return newResultPromise(duration("unlimited"));
            }
        });

        verifyZeroInteractions(executorService);
    }

    private Callable<Integer> getCallable() {
        return new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return 404;
            }
        };
    }
}
