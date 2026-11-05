package communication.network;

import communication.protocol.Role;

public record ClientInfo(int id, String name, Role role, String address) {

    @Override
    public String toString() {
        return "#" + id + " " + name + " (" + role + ") from " + address;
    }
}
