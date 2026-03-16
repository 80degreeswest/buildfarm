package build.buildfarm.plugins;

import org.pf4j.ExtensionPoint;

/**
 * Extension point for plugins that need to hook into the Buildfarm server/worker lifecycle. Called
 * after all services are started and before shutdown begins.
 */
public interface ServerLifecycleExtension extends ExtensionPoint {
  /** Called when the server or worker has started and is ready to serve. */
  void onStart();

  /** Called when the server or worker is shutting down. */
  void onStop();
}
