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
import org.springframework.shell.table.*;
import org.swordess.common.vitool.cmd.customize.Quit;
import org.swordess.common.vitool.ext.AbstractShellComponent;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@ShellComponent
public class DatabaseCommands extends AbstractShellComponent {

    private static final String ENV_VI_DB_URL = "VI_DB_URL";
    private static final String ENV_VI_DB_USERNAME = "VI_DB_USERNAME";
    private static final String ENV_VI_DB_PASSWORD = "VI_DB_PASSWORD";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private String url;
    private String username;
    private String password;

    private Connection conn;
    private DSLContext create;

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();

        Quit.onExit(it -> {
            try {
                if (availabilityCheck().isAvailable()) {
                    dbClose();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @ShellMethod(key = "db connect", value = "Establish a database connection.")
    public void dbConnect(@ShellOption(help = "jdbc url, read from the environment variable `" + ENV_VI_DB_URL + "` if not specified", defaultValue = ShellOption.NULL) String url,
                          @ShellOption(help = "username, read from the environment variable `" + ENV_VI_DB_USERNAME + "` if not specified", defaultValue = ShellOption.NULL) String username,
                          @ShellOption(help = "password, read from the environment variable `" + ENV_VI_DB_PASSWORD + "` if not specified", defaultValue = ShellOption.NULL) String password) throws SQLException {
        String urlOption = optionValue(url).orEnv(ENV_VI_DB_URL).must("`url` cannot be inferred").get();
        String usernameOption = optionValue(username).orEnv(ENV_VI_DB_USERNAME).must("`username` cannot be inferred").get();
        String passwordOption = optionValue(password).orEnv(ENV_VI_DB_PASSWORD).orInput("Enter password:").must("`password` cannot be inferred").get();

        conn = DriverManager.getConnection(urlOption, usernameOption, passwordOption);
        create = DSL.using(conn);

        System.out.println("Connection has been established.");

        this.url = urlOption;
        this.username = usernameOption;
        this.password = passwordOption;
    }

    @ShellMethod(key = "db query", value = "Execute select statements using the current connection.")
    public Object dbQuery(String sql,
                          @ShellOption(help = "the output format when displaying the result", defaultValue = "table") QueryFormat format) {
        try (ResultQuery<Record> query = create.resultQuery(sql)) {
            List<Map<String, Object>> result = query.fetchMaps();

            if (QueryFormat.json == format) {
                StringBuilder output = new StringBuilder();
                for (int i = 0; i < result.size(); i++) {
                    output.append("[").append(i).append("] = ").append(gson.toJson(result.get(i))).append("\n");
                }
                output.append(result.size()).append(" row(s) returned");
                return output;

            } else if (QueryFormat.table == format) {
                System.out.println(result.size() + " row(s) returned");

                if (!result.isEmpty()) {
                    int rowCount = result.size();
                    int colCount = result.get(0).size();

                    // one more row for header
                    Object[][] data = new Object[rowCount + 1][colCount];

                    // set header names
                    data[0] = result.get(0).keySet().toArray(new String[0]);

                    for (int i = 0; i < result.size(); i++) {
                        Map<String, Object> row = result.get(i);
                        data[i + 1] = row.values().toArray(new Object[0]);
                    }

                    TableModel model = new ArrayTableModel(data);
                    return new TableBuilder(model).addHeaderAndVerticalsBorders(BorderStyle.oldschool).build();
                }
            }
        }

        return null;
    }

    public enum QueryFormat {
        json, table
    }

    @ShellMethod(key = "db command", value = "Execute DML statements using the current connection.")
    public void dbCommand(String sql) {
        try (Query query = create.query(sql)) {
            int affectedRows = query.execute();
            System.out.println(affectedRows + " row(s) affected");
        }
    }

    @ShellMethod(key = "db close", value = "Close the current connection.")
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

    @ShellMethod(key = "db reconnect", value = "Re-establish the connection using the last successful properties.")
    public void dbReconnect() throws SQLException {
        // dbClose will clean all properties, so we must remember them before that
        String url = this.url;
        String username = this.username;
        String password = this.password;

        dbClose();
        dbConnect(url, username, password);
    }

    @ShellMethodAvailability({"db query", "db command", "db close", "db reconnect"})
    public Availability availabilityCheck() {
        return create != null ? Availability.available() : Availability.unavailable("the connection is not established");
    }

    @ShellMethodAvailability({"db connect"})
    public Availability dbConnectAvailability() {
        return create == null ? Availability.available() : Availability.unavailable("your connection is still alive");
    }

}
