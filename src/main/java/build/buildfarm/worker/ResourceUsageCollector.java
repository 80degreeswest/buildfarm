// Copyright 2026 The Buildfarm Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package build.buildfarm.worker;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import lombok.extern.java.Log;

/**
 * Collects resource utilization of a process tree by reading /proc on Linux.
 *
 * <p>This collector polls /proc/[pid]/status for peak resident set size (VmHWM) for the root
 * process and all its descendants. Since execution wrappers (cgroups, sandbox, as-nobody) spawn the
 * actual build action as a child process, we traverse the process tree to find the maximum VmHWM
 * across the entire tree. /proc entries disappear once a process is reaped, so the collector retains
 * the last successfully read peak value.
 */
@Log
class ResourceUsageCollector implements Runnable {
  private static final long POLL_INTERVAL_MS = 500;

  private final long pid;

  private volatile long maxRssKb;

  ResourceUsageCollector(long pid) {
    this.pid = pid;
  }

  long getMaxRssKb() {
    return maxRssKb;
  }

  @Override
  public void run() {
    // Perform an immediate collection before entering the polling loop,
    // so even short-lived processes get at least one sample.
    collect();
    while (!Thread.interrupted()) {
      try {
        Thread.sleep(POLL_INTERVAL_MS);
      } catch (InterruptedException e) {
        break;
      }
      collect();
    }
    // One final attempt after being interrupted (process may still be a zombie).
    collect();
  }

  private void collect() {
    long treeMax = collectTreeMaxRss(pid);
    if (treeMax > maxRssKb) {
      maxRssKb = treeMax;
    }
  }

  /**
   * Recursively collects the maximum VmHWM across a process and all its descendants.
   *
   * @return the highest VmHWM (in KB) found in the process tree rooted at {@code rootPid}, or 0 if
   *     no data could be read.
   */
  private long collectTreeMaxRss(long rootPid) {
    long best = readVmHwm(rootPid);
    for (long child : getChildPids(rootPid)) {
      long childMax = collectTreeMaxRss(child);
      if (childMax > best) {
        best = childMax;
      }
    }
    return best;
  }

  /**
   * Reads peak resident set size (VmHWM) from /proc/[pid]/status.
   *
   * <p>VmHWM is the high water mark for RSS tracked by the kernel, so any single read reflects the
   * true peak up to that point.
   */
  private long readVmHwm(long targetPid) {
    try {
      String statusContent = Files.readString(Path.of("/proc/" + targetPid + "/status"));
      for (String line : statusContent.split("\n")) {
        if (line.startsWith("VmHWM:")) {
          String[] parts = line.split("\\s+");
          if (parts.length >= 2) {
            return Long.parseLong(parts[1]);
          }
          break;
        }
      }
    } catch (NoSuchFileException e) {
      // Process already exited; return 0.
    } catch (IOException | NumberFormatException e) {
      log.log(Level.FINE, "Could not read VmHWM from /proc/" + targetPid + "/status", e);
    }
    return 0;
  }

  /**
   * Returns the PIDs of all direct children of the given process by reading
   * /proc/[pid]/task/[tid]/children.
   */
  private List<Long> getChildPids(long parentPid) {
    List<Long> children = new ArrayList<>();
    Path taskDir = Path.of("/proc/" + parentPid + "/task");
    try (DirectoryStream<Path> tasks = Files.newDirectoryStream(taskDir)) {
      for (Path task : tasks) {
        Path childrenFile = task.resolve("children");
        try {
          String content = Files.readString(childrenFile).trim();
          if (!content.isEmpty()) {
            for (String tok : content.split("\\s+")) {
              children.add(Long.parseLong(tok));
            }
          }
        } catch (NoSuchFileException e) {
          // Thread or process exited; skip.
        } catch (IOException | NumberFormatException e) {
          log.log(Level.FINE, "Could not read children from " + childrenFile, e);
        }
      }
    } catch (NoSuchFileException e) {
      // Process already exited.
    } catch (IOException e) {
      log.log(Level.FINE, "Could not list tasks for /proc/" + parentPid + "/task", e);
    }
    return children;
  }
}
