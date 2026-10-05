package com.simbirsoft.partners.shared.api;

public enum McpToolBehavior {
    READ_ONLY(true, false, true),
    MUTATING(false, false, false),
    DESTRUCTIVE(false, true, false),
    IDEMPOTENT(false, false, true);

    private final boolean readOnlyHint;
    private final boolean destructiveHint;
    private final boolean idempotentHint;

    McpToolBehavior(boolean readOnlyHint, boolean destructiveHint, boolean idempotentHint) {
        this.readOnlyHint=readOnlyHint; this.destructiveHint=destructiveHint; this.idempotentHint=idempotentHint;
    }
    public boolean readOnlyHint() { return readOnlyHint; }
    public boolean destructiveHint() { return destructiveHint; }
    public boolean idempotentHint() { return idempotentHint; }
}
