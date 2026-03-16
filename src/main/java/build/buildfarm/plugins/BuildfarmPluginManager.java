package build.buildfarm.plugins;

import build.buildfarm.common.config.BuildfarmConfigs;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import lombok.extern.java.Log;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;

/**
 * Manages the lifecycle of Buildfarm plugins using PF4J. Plugins are loaded from a configurable
 * directory.
 */
@Log
public final class BuildfarmPluginManager {
  private static volatile BuildfarmPluginManager instance;
  private final PluginManager pluginManager;

  private BuildfarmPluginManager(Path pluginsDir) {
    this.pluginManager = new DefaultPluginManager(pluginsDir);
  }

  public static BuildfarmPluginManager getInstance() {
    if (instance == null) {
      synchronized (BuildfarmPluginManager.class) {
        if (instance == null) {
          String pluginsDir = BuildfarmConfigs.getInstance().getPluginsDirectory();
          instance = new BuildfarmPluginManager(Paths.get(pluginsDir));
        }
      }
    }
    return instance;
  }

  /** Initialize and start all plugins. Call once during application startup. */
  public void startPlugins() {
    pluginManager.loadPlugins();
    pluginManager.startPlugins();
    log.info(
        String.format(
            "Loaded %d plugin(s) from %s",
            pluginManager.getStartedPlugins().size(), pluginManager.getPluginsRoots()));
  }

  /** Stop and unload all plugins. Call during application shutdown. */
  public void stopPlugins() {
    pluginManager.stopPlugins();
    log.info("All plugins stopped");
  }

  /**
   * Notify all {@link ServerLifecycleExtension} extensions that the server has started. Call after
   * the server/worker is ready to serve.
   */
  public void notifyServerStarted() {
    for (ServerLifecycleExtension ext : getExtensions(ServerLifecycleExtension.class)) {
      try {
        ext.onStart();
      } catch (Exception e) {
        log.log(Level.WARNING, "ServerLifecycleExtension.onStart failed: " + ext.getClass().getName(), e);
      }
    }
  }

  /**
   * Notify all {@link ServerLifecycleExtension} extensions that the server is stopping. Call before
   * the server/worker begins shutdown.
   */
  public void notifyServerStopping() {
    for (ServerLifecycleExtension ext : getExtensions(ServerLifecycleExtension.class)) {
      try {
        ext.onStop();
      } catch (Exception e) {
        log.log(Level.WARNING, "ServerLifecycleExtension.onStop failed: " + ext.getClass().getName(), e);
      }
    }
  }

  /** Get all extensions of a given type from loaded plugins. */
  public <T> List<T> getExtensions(Class<T> type) {
    return pluginManager.getExtensions(type);
  }

  /** Get the underlying PF4J PluginManager for advanced use cases. */
  public PluginManager getPluginManager() {
    return pluginManager;
  }
}
