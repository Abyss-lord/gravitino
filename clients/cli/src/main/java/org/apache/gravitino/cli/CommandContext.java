/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.gravitino.cli;

import com.google.common.base.Preconditions;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.gravitino.Configs;
import org.apache.gravitino.cli.commands.Command;
import org.apache.gravitino.cli.selector.BaseUriSelector;
import org.apache.gravitino.rest.RESTUtils;
import org.apache.gravitino.server.ServerConfig;

/* Context for a command */
public class CommandContext {
  public static final String DEFAULT_CONFIG_NAME = "gravitino.conf";
  private final boolean force;
  private final boolean ignoreVersions;
  private final String outputFormat;
  private final String url;
  private final boolean quiet;
  private final CommandLine line;
  private final String auth;
  private final ServerConfig serverConfig;

  private String ignoreEnv;
  private boolean ignoreSet = false;
  private String authEnv;
  private boolean authSet = false;
  // Can add more "global" command flags here without any major changes e.g. a guiet flag
  /**
   * The uris of server, if there are multiple servers, the client will try to connect to one of
   * them
   */
  private List<String> uris;

  /**
   * Command constructor.
   *
   * @param line The command line.
   */
  public CommandContext(CommandLine line) {
    Preconditions.checkNotNull(line);
    this.line = line;
    this.force = line.hasOption(GravitinoOptions.FORCE);
    this.outputFormat =
        line.hasOption(GravitinoOptions.OUTPUT)
            ? line.getOptionValue(GravitinoOptions.OUTPUT)
            : Command.OUTPUT_FORMAT_PLAIN;
    this.quiet = line.hasOption(GravitinoOptions.QUIET);
    this.serverConfig = new ServerConfig();
    try {
      this.serverConfig.loadFromFile(DEFAULT_CONFIG_NAME);
    } catch (Exception e) {
      // ignore
    }

    this.url = getUrl();
    this.ignoreVersions = getIgnore();
    this.auth = this.getAuth();
  }

  /**
   * Returns the URL.
   *
   * @return The URL.
   */
  public String url() {
    return url;
  }

  /**
   * Indicates whether versions should be ignored.
   *
   * @return False if versions should be ignored.
   */
  public boolean ignoreVersions() {
    return ignoreVersions;
  }

  /**
   * Indicates whether the operation should be forced.
   *
   * @return True if the operation should be forced.
   */
  public boolean force() {
    return force;
  }

  /**
   * Returns the output format.
   *
   * @return The output format.
   */
  public String outputFormat() {
    return outputFormat;
  }

  /**
   * Returns whether the command information should be suppressed.
   *
   * @return True if the command information should be suppressed.
   */
  public boolean quiet() {
    return quiet;
  }

  /**
   * Returns the authentication type.
   *
   * @return The authentication type.
   */
  public String auth() {
    return auth;
  }

  /**
   * Returns the command line.
   *
   * @return The command line.
   */
  public CommandLine line() {
    return line;
  }
  /**
   * Retrieves the Gravitino URL from the command line options or the GRAVITINO_URL environment
   * variable or the Gravitino config file.
   *
   * @return The Gravitino URL, or null if not found.
   */
  private String getUrl() {
    // If specified on the command line use that
    if (line.hasOption(GravitinoOptions.URL)) {
      return line.getOptionValue(GravitinoOptions.URL);
    }

    try {
      resolveUris();
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      return GravitinoCommandLine.DEFAULT_URL;
    }

    return getAvailableUri();
  }

  private void resolveUris() {
    String tmpUris = serverConfig.get(Configs.SERVER_URIS);
    if (tmpUris == null) {
      throw new IllegalArgumentException("Server URIs not found in config file");
    }

    String[] serverUrisString = tmpUris.split(",");
    uris = new ArrayList<>();
    for (String uri : serverUrisString) {
      try {
        uri = RESTUtils.stripTrailingSlash(uri);
        RESTUtils.validateUri(uri);
        uris.add(uri);

      } catch (URISyntaxException e) {
        throw new IllegalArgumentException("Invalid URI syntax: " + uri, e);
      }
    }
  }

  private String getAvailableUri() {
    return BaseUriSelector.create(serverConfig).getUri(uris);
  }

  private boolean getIgnore() {
    GravitinoConfig config = new GravitinoConfig(null);
    boolean ignore = false;

    /* Check if you should ignore client/version versions */
    if (line.hasOption(GravitinoOptions.IGNORE)) {
      ignore = true;
    } else {
      // Cache the ignore environment variable
      if (ignoreEnv == null && !ignoreSet) {
        ignoreEnv = System.getenv("GRAVITINO_IGNORE");
        ignore = ignoreEnv != null && ignoreEnv.equals("true");
        ignoreSet = true;
      }

      // Check if the ignore name is specified in the configuration file
      if (ignoreEnv == null) {
        if (config.fileExists()) {
          config.read();
          ignore = config.getIgnore();
        }
      }
    }

    return ignore;
  }

  private String getAuth() {
    // If specified on the command line use that
    if (line.hasOption(GravitinoOptions.SIMPLE)) {
      return GravitinoOptions.SIMPLE;
    }

    // Cache the Gravitino authentication type environment variable
    if (authEnv == null && !authSet) {
      authEnv = System.getenv("GRAVITINO_AUTH");
      authSet = true;
    }

    // If set return the Gravitino authentication type environment variable
    if (authEnv != null) {
      return authEnv;
    }

    // Check if the authentication type is specified in the configuration file
    GravitinoConfig config = new GravitinoConfig(null);
    if (config.fileExists()) {
      config.read();
      String configAuthType = config.getGravitinoAuthType();
      if (configAuthType != null) {
        return configAuthType;
      }
    }

    return null;
  }
}
