---
layout: default
title: Plugins
nav_order: 8
---

# Plugin System

Buildfarm supports a plugin architecture powered by [PF4J](https://pf4j.org/) (Plugin Framework for Java). Plugins allow you to extend Buildfarm's behavior without modifying its core source code. Plugin JARs are loaded at startup from a configurable directory.

## Available Extension Points

Extension points are interfaces that plugins can implement to hook into Buildfarm.

| Extension Point | Interface | Description |
|---|---|---|
| Metrics Publisher | `build.buildfarm.metrics.MetricsPublisher` | Publish operation metrics and request metadata to external systems |
| Server Lifecycle | `build.buildfarm.plugins.ServerLifecycleExtension` | Hook into server/worker startup and shutdown (e.g., start an HTTP endpoint) |

## Configuration

Set the `pluginsDirectory` in your configuration YAML to specify where Buildfarm should look for plugin JARs:

```yaml
pluginsDirectory: /path/to/plugins
```

The default value is `plugins` (relative to the working directory). The directory is scanned at server startup. Any valid PF4J plugin JARs found will be loaded and started automatically.

## Official Plugins

The official plugins are maintained in the [buildfarm-plugins](https://github.com/buildfarm/buildfarm-plugins) repository:

| Plugin | Description |
|---|---|
| `log-metrics-plugin` | Logs operation metrics and request metadata as JSON at a configurable log level |
| `prometheus-metrics-plugin` | Starts a Prometheus HTTP endpoint for metrics scraping using the `ServerLifecycleExtension` |

To use these plugins, build them from the `buildfarm-plugins` project and place the JARs in your configured `pluginsDirectory`.

## Creating a New Plugin

This section walks through creating a custom plugin from scratch. We will use a metrics publisher as the example.

### 1. Create a Plugin Class

Every plugin needs a class that extends `BuildfarmPlugin`. This is the entry point that PF4J uses to manage the plugin lifecycle:

```java
package com.example.myplugin;

import build.buildfarm.plugins.BuildfarmPlugin;

public class MyPlugin extends BuildfarmPlugin {}
```

### 2. Implement an Extension Point

Create a class that implements the desired extension point interface and annotate it with `@Extension`:

```java
package com.example.myplugin;

import build.bazel.remote.execution.v2.RequestMetadata;
import build.buildfarm.metrics.MetricsPublisher;
import com.google.longrunning.Operation;
import org.pf4j.Extension;

@Extension
public class MyMetricsPublisher implements MetricsPublisher {

  @Override
  public void publishRequestMetadata(Operation operation, RequestMetadata requestMetadata) {
    // Send metrics to your external system
  }

  @Override
  public void publishMetric(String metricName, Object metricValue) {
    // Handle individual metric values
  }
}
```

If you want to reuse the built-in metadata population and JSON formatting logic, you can extend `AbstractMetricsPublisher` instead of implementing `MetricsPublisher` directly:

```java
@Extension
public class MyMetricsPublisher extends AbstractMetricsPublisher {

  public MyMetricsPublisher() {
    super("my-cluster-id");
  }

  @Override
  public void publishRequestMetadata(Operation operation, RequestMetadata requestMetadata) {
    OperationRequestMetadata metadata = populateRequestMetadata(operation, requestMetadata);
    if (metadata != null && metadata.getDone()) {
      String json = formatRequestMetadataToJson(metadata);
      // Forward json to your metrics backend
    }
  }

  @Override
  public void publishMetric(String metricName, Object metricValue) {
    // Handle individual metric values
  }
}
```

### 3. Create a Plugin Descriptor

PF4J requires a `plugin.properties` file (or `MANIFEST.MF` entries) to identify the plugin. Place a `plugin.properties` file in your JAR's root:

```properties
plugin.id=my-metrics-plugin
plugin.class=com.example.myplugin.MyPlugin
plugin.version=1.0.0
plugin.description=Custom metrics publisher for Buildfarm
plugin.provider=My Organization
```

### 4. Package and Deploy

Build your plugin as a JAR that includes:

- Your plugin class and extension classes
- The `plugin.properties` file at the JAR root
- Any dependencies not already provided by Buildfarm (Buildfarm's classes and PF4J are available on the classpath at runtime)

Place the JAR in the configured `pluginsDirectory` and restart Buildfarm. You should see a log message on startup indicating the plugin was loaded:

```
INFO: Loaded 1 plugin(s) from [/path/to/plugins]
```

### 5. Multiple Extensions

A single plugin can provide multiple extensions. For example, a plugin JAR could contain both a `MetricsPublisher` and future extension point implementations. PF4J will discover all `@Extension`-annotated classes within the plugin.

When multiple `MetricsPublisher` extensions are found (from one or more plugins), all of them will receive every metrics event. Errors in one publisher will not prevent others from executing.

## Plugin Architecture Overview

```
┌──────────────────────────────────────────────────┐
│               Buildfarm Server/Worker            │
│                                                  │
│  ┌────────────────────────────────────────┐      │
│  │         BuildfarmPluginManager         │      │
│  │   (loads JARs from pluginsDirectory)   │      │
│  └──────────┬─────────────────────────────┘      │
│             │                                    │
│             ▼                                    │
│  ┌────────────────────────────────┐              │
│  │       Extension Points         │              │
│  │  ┌──────────────────────────┐  │              │
│  │  │    MetricsPublisher      │◄─┼── Plugin JARs│
│  │  └──────────────────────────┘  │              │
│  │  ┌──────────────────────────┐  │              │
│  │  │ ServerLifecycleExtension │◄─┼── Plugin JARs│
│  │  └──────────────────────────┘  │              │
│  └────────────────────────────────┘              │
│             │                                    │
│             ▼                                    │
│  ┌────────────────────────────────────────┐      │
│  │       CompositeMetricsPublisher        │      │
│  │  (delegates to all plugin publishers)  │      │
│  └────────────────────────────────────────┘      │
└──────────────────────────────────────────────────┘
```

## Key Classes

| Class | Description |
|---|---|
| `build.buildfarm.plugins.BuildfarmPlugin` | Base class for all plugins |
| `build.buildfarm.plugins.BuildfarmPluginManager` | Loads and manages plugin lifecycle |
| `build.buildfarm.plugins.ServerLifecycleExtension` | Extension point for server/worker startup and shutdown hooks |
| `build.buildfarm.metrics.MetricsPublisher` | Extension point interface for metrics |
| `build.buildfarm.metrics.AbstractMetricsPublisher` | Base class with Prometheus counters and metadata helpers |
| `build.buildfarm.metrics.CompositeMetricsPublisher` | Delegates to all discovered plugin publishers |
