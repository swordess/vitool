package org.swordess.common.vitool.cmd;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;
import org.springframework.shell.standard.ShellOption;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.swordess.common.vitool.util.Options.must;
import static org.swordess.common.vitool.util.Options.orElseEnv;

@ShellComponent
public class DatabaseCommands {

    private static final String ENV_VI_DB_URL = "VI_DB_URL";
    private static final String ENV_VI_DB_USERNAME = "VI_DB_USERNAME";
    private static final String ENV_VI_DB_PASSWORD = "VI_DB_PASSWORD";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private String url;
    private String username;
    private String password;

    private Connection conn;
    private DSLContext create;

    @ShellMethod("Establish a database connection.")
    public void dbConnect(@ShellOption(help = "jdbc url, read from the environment variable `" + ENV_VI_DB_URL + "` if not specified", defaultValue = ShellOption.NULL) String url,
                          @ShellOption(help = "username, read from the environment variable `" + ENV_VI_DB_USERNAME + "` if not specified", defaultValue = ShellOption.NULL) String username,
                          @ShellOption(help = "password, read from the environment variable `" + ENV_VI_DB_PASSWORD + "` if not specified", defaultValue = ShellOption.NULL) String password) throws SQLException {
        String urlOption = must(orElseEnv(url, ENV_VI_DB_URL), "`url` cannot be inferred");
        String usernameOption = must(orElseEnv(username, ENV_VI_DB_USERNAME), "`username` cannot be inferred");
        String passwordOption = must(orElseEnv(password, ENV_VI_DB_PASSWORD), "`password` cannot be inferred");

        conn = DriverManager.getConnection(urlOption, usernameOption, passwordOption);
        create = DSL.using(conn);

        System.out.println("Connection has been established.");

        this.url = urlOption;
        this.username = usernameOption;
        this.password = passwordOption;
    }

    @ShellMethod("Execute select statements using the current connection.")
    public void dbQuery(String sql) {
        try (ResultQuery<Record> query = create.resultQuery(sql)) {
            List<Map<String, Object>> result = query.fetchMaps();
            for (int i = 0; i < result.size(); i++) {
                System.out.println("[" + i + "] = " + gson.toJson(result.get(i)));
            }
            System.out.println(result.size() + " row(s) returned");
        }
    }

    @ShellMethod("Execute DML statements using the current connection.")
    public void dbCommand(String sql) {
        try (Query query = create.query(sql)) {
            int affectedRows = query.execute();
            System.out.println(affectedRows + " row(s) affected");
        }
    }

    @ShellMethod("Close the current connection.")
    public void dbClose() throws SQLException {
        try {
            conn.close();
            System.out.println("Connection has been closed.");
        } finally {
            conn = null;
            create = null;

            url = null;
            username = null;
            password = null;
        }
    }

    @ShellMethod("Re-establish the connection using the last successful properties.")
    public void dbReconnect() throws SQLException {
        // dbClose will clean all properties, so we must remember them before that
        String url = this.url;
        String username = this.username;
        String password = this.password;

        dbClose();
        dbConnect(url, username, password);
    }

    @ShellMethodAvailability({"db-query", "db-command", "db-close", "db-reconnect"})
    public Availability availabilityCheck() {
        return create != null ? Availability.available() : Availability.unavailable("the connection is not established");
    }

    public Availability dbConnectAvailability() {
        return create == null ? Availability.available() : Availability.unavailable("your connection is still alive");
    }

}
