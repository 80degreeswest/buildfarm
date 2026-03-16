package build.buildfarm.metrics;

import build.bazel.remote.execution.v2.RequestMetadata;
import build.buildfarm.plugins.BuildfarmPluginManager;
import com.google.longrunning.Operation;
import java.util.List;
import java.util.logging.Level;
import lombok.extern.java.Log;

/**
 * A MetricsPublisher that delegates to all MetricsPublisher extensions discovered via the plugin
 * system. If no plugin-provided publishers are found, metrics calls are no-ops.
 */
@Log
public class CompositeMetricsPublisher implements MetricsPublisher {
  private final List<MetricsPublisher> publishers;

  public CompositeMetricsPublisher() {
    this.publishers =
        BuildfarmPluginManager.getInstance().getExtensions(MetricsPublisher.class);
    if (publishers.isEmpty()) {
      log.warning(
          "No MetricsPublisher plugins found. Install a metrics plugin (e.g. log-metrics-plugin)"
              + " to enable metrics publishing.");
    } else {
      log.info("Found " + publishers.size() + " MetricsPublisher plugin(s)");
    }
  }

  @Override
  public void publishRequestMetadata(Operation operation, RequestMetadata requestMetadata) {
    for (MetricsPublisher publisher : publishers) {
      try {
        publisher.publishRequestMetadata(operation, requestMetadata);
      } catch (Exception e) {
        log.log(
            Level.WARNING,
            "MetricsPublisher plugin failed to publish request metadata: "
                + publisher.getClass().getName(),
            e);
      }
    }
  }

  @Override
  public void publishMetric(String metricName, Object metricValue) {
    for (MetricsPublisher publisher : publishers) {
      try {
        publisher.publishMetric(metricName, metricValue);
      } catch (Exception e) {
        log.log(
            Level.WARNING,
            "MetricsPublisher plugin failed to publish metric: "
                + publisher.getClass().getName(),
            e);
      }
    }
  }
}
