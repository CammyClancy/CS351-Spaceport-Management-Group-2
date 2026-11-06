# Client & Communication

The client application, the protocol, networking and notifications. The code is in
`src/java/communication/` and its unit tests are in `src/unitTest/communication/`. This part does not
depend on any other part of the project: the rest of the server plugs into it through two things.

## Project setup in IntelliJ

`src/java` is the Sources Root and `src/unitTest` is the Test Sources Root. Each folder inside them is
the start of a package name, e.g. `src/java/communication/client` is the package `communication.client`.

The `.idea` folder is not in git, so after cloning, each person marks the two roots once: right-click
`src/java` → Mark Directory as → Sources Root, and `src/unitTest` → Mark Directory as → Test Sources
Root. Then open any test and press Alt+Enter on `@Test` → "Add JUnit 5 to classpath".

## Plugging in the rest of the server

1. **Handle commands:** implement `communication.network.CommandHandler` and pass it to the server:

   ```java
   PortWanderServer server = new PortWanderServer(8080, 20, new MySpaceportCommandHandler());
   server.start();
   ```

   `handle(request, client)` is called from many client threads at once, so it must be thread-safe.
   Use `request.getArgument(i)` for IDs and `request.getPositiveNumber(i)` for amounts. Return
   `Response.ok(...)` or `Response.error(...)`. Use `Response.ok(message, lines)` for tables.

2. **Send notifications:** after a successful operation (or a departure), call

   ```java
   server.getNotifications().broadcast("T001 departed from BAY_02");
   ```

   It returns straight away, so it's safe to call from any thread.

`HELLO`, `QUIT`, `VIEW_CLIENTS` and the admin-permission check are handled here and never reach your
`CommandHandler`.

## Protocol

One message per line.

| Message | Format | Example |
|---|---|---|
| Request | `COMMAND arg1 arg2` | `LOAD_CARGO T001 25` |
| Response | `OK message` or `ERR message`, then `DATA line` (0 or more), then `END` | `OK 2 transports` / `DATA T001 DOCKED` / `DATA T002 READY` / `END` |
| Notification | `NOTIFY text` | `NOTIFY T001 departed` |

The full command list with arguments is in `protocol/CommandType.java`.

## Running

- Server: run `communication.network.PortWanderServer` (port 8080; replies "not available yet" until
  a real `CommandHandler` is plugged in).
- Client: run `communication.client.ClientApp`, or add the program argument `--admin` for an admin
  terminal.
- Tests: right-click `src/unitTest/communication` → Run 'Tests in communication'.

## Known limitations

- The admin role is chosen by the terminal (`--admin`); there is no password.
- Notifications are sent by one background thread. A terminal that stays connected but stops reading
  would hold up notifications to the others until it disconnects.
- At most 20 terminals are served at once (the thread pool size); extra ones wait for a free thread.
