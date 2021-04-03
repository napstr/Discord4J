package discord4j.gateway.retry;

import discord4j.common.close.CloseStatus;
import discord4j.common.close.DisconnectBehavior;

public class ClosingStateChange extends GatewayStateChange {
    private final DisconnectBehavior behavior;
    private final CloseStatus status;

    protected static ClosingStateChange resuming(DisconnectBehavior behavior, CloseStatus status) {
        return new ClosingStateChange(State.DISCONNECTED_RESUME, behavior, status);
    }

    protected static ClosingStateChange disconnecting(DisconnectBehavior behavior, CloseStatus status) {
        return new ClosingStateChange(State.DISCONNECTED, behavior, status);
    }

    protected ClosingStateChange(State state, DisconnectBehavior behavior, CloseStatus status) {
        super(state, 0, null);
        this.behavior = behavior;
        this.status = status;
    }

    public DisconnectBehavior getBehavior() {
        return behavior;
    }

    public CloseStatus getStatus() {
        return status;
    }
}
