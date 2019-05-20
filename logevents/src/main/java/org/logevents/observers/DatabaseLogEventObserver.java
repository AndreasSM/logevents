package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.batch.LogEventBatchProcessor;
import org.logevents.status.LogEventStatus;
import org.logevents.util.Configuration;
import org.logevents.util.JsonParser;
import org.logevents.util.JsonUtil;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

// TODO: Save exception
// TODO: Configurable table names
public class DatabaseLogEventObserver extends BatchingLogEventObserver implements LogEventBatchProcessor {

    private final String jdbcUrl;
    private final String jdbcUsername;
    private final String jdbcPassword;
    private final String nodeName;

    public DatabaseLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public DatabaseLogEventObserver(Configuration configuration) {
        super(null);
        this.jdbcUrl = configuration.getString("jdbcUrl");
        this.jdbcUsername = configuration.getString("jdbcUsername");
        this.jdbcPassword = configuration.optionalString("jdbcPassword").orElse("");
        this.nodeName = configuration.getNodeName();

        try (Connection connection = getConnection()) {
            try (ResultSet tables = connection.getMetaData().getTables(null, null, "LOG_EVENT", null)) {
                if (!tables.next()) {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate("create table log_events (" +
                                "event_id varchar(100) primary key, " +
                                "logger varchar(100) not null, " +
                                "level varchar(10) not null, " +
                                "level_int integer not null, " +
                                "message text not null, " +
                                "instant bigint not null, " +
                                "thread_name varchar(100) not null," +
                                "arguments varchar(1000) not null," +
                                "node_name varchar(100) not null," +
                                "marker varchar(100)" +
                                ")"
                        );
                        statement.executeUpdate("create index log_events_logger_idx on log_events(logger)");
                        statement.executeUpdate("create index log_events_level_idx on log_events(level)");
                        statement.executeUpdate("create index log_events_instant_idx on log_events(instant)");
                        statement.executeUpdate("create index log_events_thread_name_idx on log_events(thread_name)");
                        statement.executeUpdate("create index log_events_marker_idx on log_events(marker)");
                        statement.executeUpdate("create index log_events_node_name_idx on log_events(node_name)");
                    }
                }
            }
            try (ResultSet tables = connection.getMetaData().getTables(null, null, "LOG_EVENT_MDC", null)) {
                if (!tables.next()) {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate("create table log_events_mdc (" +
                                "event_id varchar(100) not null references log_events(event_id), " +
                                "key varchar(100) not null, " +
                                "value varchar(20) not null" +
                                ")"
                        );
                        statement.executeUpdate("create index log_events_mdc_idx on log_events_mdc(key, value)");
                    }
                }
            }
        } catch (SQLException e) {
            LogEventStatus.getInstance().addFatal(this, "Failed to initialize database", e);
        }
    }

    private String toString(Object o) {
        return Optional.ofNullable(o).map(Object::toString).orElse(null);
    }

    public Collection<LogEvent> filter(Level threshold, Instant start, Instant end) {
        Collection<LogEvent> result = new ArrayList<>();
        Map<String, LogEvent> idToResult = new HashMap<>();
        try (Connection connection = getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("select * from log_events e left outer join log_events_mdc m on e.event_id = m.event_id where instant between ? and ? and level_int >= ?")) {
                statement.setLong(1, start.toEpochMilli());
                statement.setLong(2, end.toEpochMilli());
                statement.setInt(3, threshold.toInt());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("event_id");
                        LogEvent e = idToResult.get(id);
                        if (e == null) {
                            e = new LogEvent(
                                    rs.getString("logger"),
                                    Level.valueOf(rs.getString("level")),
                                    rs.getString("thread_name"),
                                    Instant.ofEpochMilli(rs.getLong("instant")),
                                    getMarker(rs.getString("marker")),
                                    rs.getString("message"),
                                    JsonParser.parseArray(rs.getString("arguments")).toArray(),
                                    null,
                                    new HashMap<>()
                            );
                            idToResult.put(id, e);
                        }
                        if (rs.getString("key") != null) {
                            e.getMdcProperties().put(rs.getString("key"), rs.getString("value"));
                        }
                        result.add(e);
                    }
                }
            }
        } catch (SQLException e) {
            LogEventStatus.getInstance().addError(this, "Failed to write log record", e);
        }
        return result;
    }

    private Marker getMarker(String marker) {
        return marker != null ? MarkerFactory.getMarker(marker) : null;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, jdbcUsername, jdbcPassword);
    }

    @Override
    public void processBatch(LogEventBatch batch) {
        try (Connection connection = getConnection()) {
            try (
                    PreparedStatement eventStmt = connection.prepareStatement("insert into log_events (event_id, logger, level, level_int, message, instant, thread_name, arguments, marker, node_name) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                    PreparedStatement mdcStmt = connection.prepareStatement("insert into log_events_mdc (event_id, key, value) values (?, ?, ?)")
                    ) {
                for (LogEvent logEvent : batch) {
                    String id = UUID.randomUUID().toString();
                    eventStmt.setString(1, id);
                    eventStmt.setString(2, logEvent.getLoggerName());
                    eventStmt.setString(3, logEvent.getLevel().toString());
                    eventStmt.setInt(4, logEvent.getLevel().toInt());
                    eventStmt.setString(5, logEvent.getMessage());
                    eventStmt.setLong(6, logEvent.getTimeStamp());
                    eventStmt.setString(7, logEvent.getThreadName());
                    eventStmt.setString(8, JsonUtil.toIndentedJson(Arrays.asList(logEvent.getArgumentArray())));
                    eventStmt.setString(9, toString(logEvent.getMarker()));
                    eventStmt.setString(10, nodeName);
                    eventStmt.addBatch();

                    for (Map.Entry<String, String> entry : logEvent.getMdcProperties().entrySet()) {
                        mdcStmt.setString(1, id);
                        mdcStmt.setString(2, entry.getKey());
                        mdcStmt.setString(3,entry.getValue());
                        mdcStmt.addBatch();
                    }

                }
                eventStmt.executeBatch();
                mdcStmt.executeBatch();
            }
        } catch (SQLException e) {
            LogEventStatus.getInstance().addError(this, "Failed to write log record", e);
        }
    }
}
