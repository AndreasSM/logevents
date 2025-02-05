package org.logevents.observers;

import org.logevents.config.Configuration;
import org.logevents.extend.servlets.LogEventsServlet;
import org.logevents.observers.batch.BatchThrottler;
import org.logevents.observers.batch.ExecutorScheduler;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.batch.SlackLogEventsFormatter;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Writes log events as asynchronous batches to Slack. Slack is a great destination for logging as it
 * provides great notification support on mobile and desktop platforms. Requires
 * a Slack Web Hook, which you can create as a <a href="https://www.slack.com/apps/manage/custom-integrations">
 * Slack Custom Integration</a>.
 * <p>
 * Example configuration:
 * <pre>
 * observer.slack=SlackLogEventObserver
 * observer.slack.slackUrl=https://hooks.slack.com/services/XXXX/XXX/XXX
 * observer.slack.threshold=WARN
 * observer.slack.slackLogEventsFormatter={@link SlackLogEventsFormatter}
 * observer.slack.showRepeatsIndividually=false
 * observer.slack.channel=alertChannel
 * observer.slack.iconEmoji=:ant:
 * observer.slack.cooldownTime=PT10S
 * observer.slack.maximumWaitTime=PT5M
 * observer.slack.idleThreshold=PT5S
 * observer.slack.suppressMarkers=BORING_MARKER
 * observer.slack.requireMarker=MY_MARKER, MY_OTHER_MARKER
 * observer.slack.detailUrl=link to your {@link LogEventsServlet}
 * observer.slack.packageFilter=sun.www, com.example.uninteresting
 * observer.slack.includedMdcKeys=clientIp
 * observer.slack.markers.MY_MARKER.throttle=PT1M PT10M PT30M
 * observer.slack.markers.MY_MARKER.emoji=:rocket:
 * </pre>
 *
 * @see BatchingLogEventObserver
 * @see MicrosoftTeamsLogEventObserver
 * @see BatchThrottler
 */
public class SlackLogEventObserver extends HttpPostJsonLogEventObserver {

    private final SlackLogEventsFormatter formatter;

    public SlackLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public SlackLogEventObserver(Configuration configuration) {
        super(configuration.optionalUrl("slackUrl").orElse(null));
        this.formatter = setupFormatter(configuration);

        configureFilter(configuration);
        configureBatching(configuration);
        configureMarkers(configuration);

        configuration.checkForUnknownFields();
    }

    public SlackLogEventObserver(URL slackUrl, Optional<String> username, Optional<String> channel) {
        this(slackUrl, new SlackLogEventsFormatter(username, channel));
    }

    public SlackLogEventObserver(URL slackUrl, SlackLogEventsFormatter formatter) {
        super(slackUrl);
        this.formatter = formatter;
    }

    protected SlackLogEventsFormatter setupFormatter(Configuration configuration) {
        SlackLogEventsFormatter formatter = createFormatter(configuration);
        formatter.setUsername(
                Optional.ofNullable(configuration.optionalString("username")
                        .orElseGet(configuration::getApplicationNode))
        );
        List<String> packageFilters = configuration.getStringList("packageFilter");
        if (packageFilters.isEmpty()) {
            packageFilters = configuration.getDefaultStringList("packageFilter");
        }
        formatter.setPackageFilter(packageFilters);
        List<String> includedMdcKeys = configuration.getStringList("includedMdcKeys");
        if (includedMdcKeys.isEmpty()) {
            includedMdcKeys = configuration.getDefaultStringList("includedMdcKeys");
        }
        formatter.setIncludedMdcKeys(includedMdcKeys);
        formatter.setChannel(configuration.optionalString("channel"));
        formatter.setIconEmoji(configuration.optionalString("iconEmoji"));
        formatter.setShowRepeatsIndividually(configuration.getBoolean("showRepeatsIndividually"));
        formatter.setDetailUrl(configuration.optionalString("detailUrl"));
        formatter.configureSourceCode(configuration);

        return formatter;
    }

    protected SlackLogEventsFormatter createFormatter(Configuration configuration) {
        return configuration.createInstanceWithDefault("formatter", SlackLogEventsFormatter.class);
    }

    @Override
    protected BatchThrottler createBatcher(Configuration configuration, String markerName) {
        SlackLogEventsFormatter formatter = setupFormatter(configuration);
        configuration.optionalString("markers." + markerName + ".channel")
                .ifPresent(channel -> formatter.setChannel(Optional.of(channel)));
        configuration.optionalString("markers." + markerName + ".emoji")
                .ifPresent(emoji -> formatter.setIconEmoji(Optional.of(emoji)));
        String throttle = configuration.getString("markers." + markerName + ".throttle");
        return new BatchThrottler(
                new ExecutorScheduler(scheduledExecutorService, shutdownHook),
                this::processBatch
        ).setThrottle(throttle);
    }

    @Override
    protected Map<String, Object> formatBatch(LogEventBatch batch) {
        return formatter.createMessage(batch);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{formatter=" + formatter + ",url=" + getUrl() + '}';
    }

}
