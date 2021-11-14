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

package discord4j.gateway;

import discord4j.common.ReactorResources;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import java.util.function.Supplier;

/**
 * Provides an extra level of configuration for {@link ReactorResources}, tailored for the Gateway operations.
 * <p>
 * Allows customizing the {@link Scheduler} used to send gateway payloads.
 */
public class GatewayReactorResources extends ReactorResources {

    public static final Supplier<Scheduler> DEFAULT_PAYLOAD_SENDER_SCHEDULER = () ->
            Schedulers.newSingle("d4j-gateway", true);
    public static final Supplier<Scheduler> DEFAULT_PUBLISH_SCHEDULER = () ->
            Schedulers.newParallel("d4j-gateway-publish");

    private final Scheduler payloadSenderScheduler;
    private final Scheduler publishScheduler;

    /**
     * Create Gateway resources based off {@link ReactorResources} properties, and providing defaults for the
     * remaining properties.
     *
     * @param parent the resources instance to get properties from
     */
    public GatewayReactorResources(ReactorResources parent) {
        super(parent.getHttpClient(), parent.getTimerTaskScheduler(), parent.getBlockingTaskScheduler());
        this.payloadSenderScheduler = DEFAULT_PAYLOAD_SENDER_SCHEDULER.get();
        this.publishScheduler = DEFAULT_PUBLISH_SCHEDULER.get();
    }

    /**
     * Create Gateway resources based off {@link ReactorResources} properties, and allowing customization of the
     * remaining properties.
     *
     * @param parent the resources instance to get properties from
     * @param payloadSenderScheduler a {@link Scheduler} for sending payloads. A default can be created from
     * {@link GatewayReactorResources#DEFAULT_PAYLOAD_SENDER_SCHEDULER}
     */
    public GatewayReactorResources(ReactorResources parent, Scheduler payloadSenderScheduler) {
        super(parent.getHttpClient(), parent.getTimerTaskScheduler(), parent.getBlockingTaskScheduler());
        this.payloadSenderScheduler = payloadSenderScheduler;
        this.publishScheduler = DEFAULT_PUBLISH_SCHEDULER.get(); // TODO: add to parameters for 3.3
    }

    /**
     * Create Gateway resources allowing full customization of its properties.
     *
     * @param httpClient the HTTP client to use for initiating Gateway websocket connections. A default is provided
     * in {@link ReactorResources#DEFAULT_HTTP_CLIENT}
     * @param timerTaskScheduler the scheduler for timed tasks. A default can be created from
     * {@link ReactorResources#DEFAULT_TIMER_TASK_SCHEDULER}
     * @param blockingTaskScheduler the scheduler for blocking tasks. A default can be created from
     * {@link ReactorResources#DEFAULT_BLOCKING_TASK_SCHEDULER}
     * @param payloadSenderScheduler a scheduler for sending payloads. A default can be created from
     * {@link GatewayReactorResources#DEFAULT_PAYLOAD_SENDER_SCHEDULER}
     */
    public GatewayReactorResources(HttpClient httpClient, Scheduler timerTaskScheduler,
                                   Scheduler blockingTaskScheduler, Scheduler payloadSenderScheduler) {
        super(httpClient, timerTaskScheduler, blockingTaskScheduler);
        this.payloadSenderScheduler = payloadSenderScheduler;
        this.publishScheduler = DEFAULT_PUBLISH_SCHEDULER.get(); // TODO: add to parameters for 3.3
    }

    protected GatewayReactorResources(Builder builder) {
        super(builder);

        this.payloadSenderScheduler = builder.payloadSenderScheduler == null ?
                DEFAULT_PAYLOAD_SENDER_SCHEDULER.get() : builder.payloadSenderScheduler;
        this.publishScheduler = builder.publishScheduler == null ?
                DEFAULT_PUBLISH_SCHEDULER.get() : builder.publishScheduler;
    }

    /**
     * Create a default set of Gateway resources.
     *
     * @return a new {@link GatewayReactorResources} using all default properties
     */
    public static GatewayReactorResources create() {
        return new GatewayReactorResources(new ReactorResources());
    }

    /**
     * Returns a new builder to create {@link GatewayReactorResources}.
     *
     * @return a builder to create {@link GatewayReactorResources}
     */
    public static GatewayReactorResources.Builder builder() {
        return new GatewayReactorResources.Builder();
    }

    /**
     * Returns a new builder to create {@link GatewayReactorResources} from a pre-configured {@link ReactorResources},
     * copying its settings.
     *
     * @return a builder to create {@link GatewayReactorResources} with settings copied from parent resources
     */
    public static GatewayReactorResources.Builder builder(ReactorResources reactorResources) {
        return builder()
                .httpClient(reactorResources.getHttpClient())
                .timerTaskScheduler(reactorResources.getTimerTaskScheduler())
                .blockingTaskScheduler(reactorResources.getBlockingTaskScheduler());
    }

    /**
     * Returns a builder to create a new {@link GatewayReactorResources} with settings copied from the current
     * {@link GatewayReactorResources}.
     *
     * @return a builder based off this instance properties
     */
    public Builder mutate() {
        return new Builder()
                .httpClient(getHttpClient())
                .timerTaskScheduler(getTimerTaskScheduler())
                .blockingTaskScheduler(getBlockingTaskScheduler())
                .payloadSenderScheduler(getPayloadSenderScheduler());
    }

    /**
     * Get the scheduler used for sending gateway payloads.
     *
     * @return a scheduler for payload tasks
     */
    public Scheduler getPayloadSenderScheduler() {
        return payloadSenderScheduler;
    }

    /**
     * Get the scheduler used for publishing gateway payloads.
     *
     * @return a scheduler for payload publishing
     */
    public Scheduler getPublishScheduler() {
        return publishScheduler;
    }

    /**
     * Builder for {@link GatewayReactorResources}.
     */
    public static class Builder extends ReactorResources.Builder {

        private Scheduler payloadSenderScheduler;
        private Scheduler publishScheduler;

        protected Builder() {
        }

        /**
         * Set the {@link Scheduler} used for sending Gateway payloads. A default can be created from
         * {@link GatewayReactorResources#DEFAULT_PAYLOAD_SENDER_SCHEDULER}.
         *
         * @param payloadSenderScheduler a scheduler for payload tasks
         * @return this builder
         */
        public Builder payloadSenderScheduler(Scheduler payloadSenderScheduler) {
            this.payloadSenderScheduler = payloadSenderScheduler;
            return this;
        }

        /**
         * Set the {@link Scheduler} used for publishing received Gateway payloads. A default can be created from
         * {@link GatewayReactorResources#DEFAULT_PUBLISH_SCHEDULER}.
         *
         * @param publishScheduler a scheduler for publishing received gateway payloads
         * @return this builder
         */
        public Builder publishScheduler(Scheduler publishScheduler) {
            this.publishScheduler = publishScheduler;
            return this;
        }

        @Override
        public Builder httpClient(HttpClient httpClient) {
            super.httpClient(httpClient);
            return this;
        }

        @Override
        public Builder timerTaskScheduler(Scheduler timerTaskScheduler) {
            super.timerTaskScheduler(timerTaskScheduler);
            return this;
        }

        @Override
        public Builder blockingTaskScheduler(Scheduler blockingTaskScheduler) {
            super.blockingTaskScheduler(blockingTaskScheduler);
            return this;
        }

        /**
         * Creates a new instance of {@link GatewayReactorResources}.
         *
         * @return a new instance of {@link GatewayReactorResources}
         */
        @Override
        public GatewayReactorResources build() {
            return new GatewayReactorResources(this);
        }

    }
}
