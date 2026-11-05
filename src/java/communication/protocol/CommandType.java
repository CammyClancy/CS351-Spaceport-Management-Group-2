package communication.protocol;

public enum CommandType {
    HELLO(2, false, "HELLO <name> <OPERATOR|ADMIN>"),
    QUIT(0, false, "QUIT"),

    VIEW_ALL(0, false, "VIEW_ALL"),
    VIEW_TRANSPORTS(0, false, "VIEW_TRANSPORTS"),
    VIEW_BAYS(0, false, "VIEW_BAYS"),
    VIEW_TEAMS(0, false, "VIEW_TEAMS"),
    VIEW_CARGO(0, false, "VIEW_CARGO"),
    VIEW_LOG(0, false, "VIEW_LOG"),

    ALLOCATE_BAY(2, false, "ALLOCATE_BAY <transportId> <bayId>"),
    EXCHANGE_BAYS(2, false, "EXCHANGE_BAYS <transportId> <otherTransportId>"),
    ASSIGN_TEAM(2, false, "ASSIGN_TEAM <transportId> <teamId>"),
    COMPLETE_MAINTENANCE(1, false, "COMPLETE_MAINTENANCE <transportId>"),

    LOAD_CARGO(2, false, "LOAD_CARGO <transportId> <amount>"),
    UNLOAD_CARGO(2, false, "UNLOAD_CARGO <transportId> <amount>"),
    TRANSFER_CARGO(3, false, "TRANSFER_CARGO <fromTransportId> <toTransportId> <amount>"),

    ADD_TRANSPORT(3, true, "ADD_TRANSPORT <transportId> <capacity> <departureTime>"),
    CLOSE_BAY(1, true, "CLOSE_BAY <bayId>"),
    REOPEN_BAY(1, true, "REOPEN_BAY <bayId>"),
    VIEW_CLIENTS(0, true, "VIEW_CLIENTS");

    private final int argumentCount;
    private final boolean adminOnly;
    private final String usage;

    CommandType(int argumentCount, boolean adminOnly, String usage) {
        this.argumentCount = argumentCount;
        this.adminOnly = adminOnly;
        this.usage = usage;
    }

    public int getArgumentCount() {
        return argumentCount;
    }

    public boolean isAdminOnly() {
        return adminOnly;
    }

    public String getUsage() {
        return usage;
    }
}
