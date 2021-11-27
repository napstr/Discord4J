/*
 * This file is part of Discord4J.
 *
 * Discord4J is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Discord4J is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Discord4J. If not, see <http://www.gnu.org/licenses/>.
 */

package discord4j.gateway.retry;

import discord4j.common.close.CloseException;
import discord4j.common.retry.ReconnectContext;
import discord4j.common.retry.ReconnectOptions;
import discord4j.gateway.GatewayConnection;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import static reactor.function.TupleUtils.function;

public class GatewayRetrySpec extends Retry {

    public static final List<Integer> NON_RETRYABLE_STATUS_CODES = Arrays.asList(
            4004, // Authentication failed
            4010, // Invalid shard
            4011, // Sharding required
            4012, // Invalid API version
            4013, // Invalid intent(s)
            4014 // Disallowed intent(s)
    );

    private static final Function<GatewayRetrySignal, Mono<Void>> NO_OP_CONSUMER = retrySignal -> Mono.empty();

    private final ReconnectOptions reconnectOptions;
    private final ReconnectContext reconnectContext;
    private final Function<GatewayRetrySignal, Tuple2<GatewayRetrySignal, Mono<Void>>> doPreRetry;

    GatewayRetrySpec(ReconnectOptions reconnectOptions, ReconnectContext reconnectContext,
                     Function<GatewayRetrySignal, Mono<Void>> doPreRetry) {
        this.reconnectOptions = reconnectOptions;
        this.reconnectContext = reconnectContext;
        this.doPreRetry = signal -> Tuples.of(signal, doPreRetry.apply(signal));
    }

    public static GatewayRetrySpec create(ReconnectOptions reconnectOptions, ReconnectContext reconnectContext) {
        return new GatewayRetrySpec(reconnectOptions, reconnectContext, NO_OP_CONSUMER);
    }

    public GatewayRetrySpec doBeforeRetry(Function<GatewayRetrySignal, Mono<Void>> doBeforeRetry) {
        return new GatewayRetrySpec(reconnectOptions, reconnectContext,
                doPreRetry.andThen(function((signal, mono) ->
                        mono.then(doBeforeRetry.apply(signal))
                ))
        );
    }

    private boolean isRetryable(@Nullable Throwable t) {
        if (t instanceof CloseException) {
            CloseException closeException = (CloseException) t;
            return !NON_RETRYABLE_STATUS_CODES.contains(closeException.getCode());
        }
        return !(t instanceof PartialDisconnectException);
    }

    private boolean canResume(Throwable t) {
        if (t instanceof CloseException) {
            CloseException closeException = (CloseException) t;
            return closeException.getCode() < 4000;
        }
        return !(t instanceof InvalidSessionException);
    }

    @Override
    public Flux<Long> generateCompanion(Flux<RetrySignal> t) {
        return t.concatMap(retryWhenState -> {
            RetrySignal copy = retryWhenState.copy();
            Throwable currentFailure = copy.failure();

            /*
             * Gateway exceptions come in many flavors, some can be recovered through RESUME while others
             * implicitly or explicitly invalidate a session and then only a RECONNECT is possible.
             */

            // First, if the current failure is not retryable, immediately forward the error

            if (currentFailure == null) {
                return Mono.error(new IllegalStateException("Retry.RetrySignal#failure() not expected to be null"));
            }

            if (!isRetryable(currentFailure)) {
                return Mono.error(currentFailure);
            }

            if (currentFailure instanceof InvalidSessionException) {
                reconnectContext.reset();
            }
            long iteration = reconnectContext.getAttempts();

            if (iteration >= reconnectOptions.getMaxRetries()) {
                return Mono.error(Exceptions.retryExhausted("Retries exhausted: " +
                        iteration + "/" + reconnectOptions.getMaxRetries(), copy.failure()));
            }

            // whenever we can recover with RESUME, we will use zero backoff for this attempt

            Duration nextBackoff;
            GatewayConnection.State nextState;

            Duration minBackoff = reconnectOptions.getFirstBackoff();
            Duration maxBackoff = reconnectOptions.getMaxBackoffInterval();

            if (canResume(currentFailure)) {
                // RESUME can happen immediately, but we still need backoff for iteration > 1 to avoid spam
                if (iteration == 1) {
                    nextBackoff = Duration.ZERO;
                } else {
                    nextBackoff = computeBackoff(iteration - 2, minBackoff, maxBackoff);
                }
                nextState = GatewayConnection.State.RESUMING;
            } else {
                nextBackoff = computeBackoff(iteration - 1, minBackoff, maxBackoff);
                nextState = GatewayConnection.State.RECONNECTING;
            }

            reconnectContext.next();

            if (nextBackoff.isZero()) {
                return applyHooks(new GatewayRetrySignal(copy.failure(), iteration, nextBackoff, nextState),
                        Mono.just(iteration),
                        doPreRetry);
            }

            Duration effectiveBackoff = nextBackoff.plusMillis(computeJitter(nextBackoff, minBackoff, maxBackoff,
                    reconnectOptions.getJitterFactor()));

            return applyHooks(new GatewayRetrySignal(copy.failure(), iteration, effectiveBackoff, nextState),
                    Mono.delay(effectiveBackoff, reconnectOptions.getBackoffScheduler()),
                    doPreRetry);
        });
    }

    static long computeJitter(Duration nextBackoff, Duration minBackoff, Duration maxBackoff, double factor) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        long jitterOffset;
        try {
            jitterOffset = nextBackoff.multipliedBy((long) (100 * factor))
                    .dividedBy(100)
                    .toMillis();
        } catch (ArithmeticException ae) {
            jitterOffset = Math.round(Long.MAX_VALUE * factor);
        }
        long lowBound = Math.max(minBackoff.minus(nextBackoff).toMillis(), -jitterOffset);
        long highBound = Math.min(maxBackoff.minus(nextBackoff).toMillis(), jitterOffset);

        long jitter;
        if (highBound == lowBound) {
            if (highBound == 0) {
                return 0;
            } else {
                return random.nextLong(highBound);
            }
        } else {
            return random.nextLong(lowBound, highBound);
        }
    }

    static Duration computeBackoff(long iteration, Duration minBackoff, Duration maxBackoff) {
        Duration nextBackoff;
        try {
            nextBackoff = minBackoff.multipliedBy((long) Math.pow(2, iteration));
            if (nextBackoff.compareTo(maxBackoff) > 0) {
                nextBackoff = maxBackoff;
            }
        } catch (ArithmeticException overflow) {
            nextBackoff = maxBackoff;
        }
        return nextBackoff;
    }

    static <T> Mono<T> applyHooks(GatewayRetrySignal retrySignal,
                                  Mono<T> originalCompanion,
                                  final Function<GatewayRetrySignal, Tuple2<GatewayRetrySignal, Mono<Void>>> doPreRetry) {

        Mono<Void> preRetry;
        try {
            preRetry = doPreRetry.apply(retrySignal).getT2();
        } catch (Throwable e) {
            return Mono.error(e);
        }
        return preRetry.then(originalCompanion);
    }

}
