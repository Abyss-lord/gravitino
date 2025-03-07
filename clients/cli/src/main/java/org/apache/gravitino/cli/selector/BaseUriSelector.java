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

package org.apache.gravitino.cli.selector;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.gravitino.Configs;
import org.apache.gravitino.server.ServerConfig;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseUriSelector {
  private static final Logger LOG = LoggerFactory.getLogger(BaseUriSelector.class.getName());

  private static final String CONFIG_API = "configs";
  protected final ServerConfig config;

  /**
   * Construct a new instance of {@link BaseUriSelector}.
   *
   * @param config the server configuration.
   */
  public BaseUriSelector(ServerConfig config) {
    this.config = config;
  }

  /**
   * Create a new instance of {@link BaseUriSelector} based on the server configuration.
   *
   * @param config the server configuration.
   * @return a new instance of {@link BaseUriSelector}.
   */
  public static BaseUriSelector create(ServerConfig config) {
    return new DefaultUriSelector(config);
  }

  /**
   * Select a URI from a list of URIs.
   *
   * @param uris the list of URIs to select from.
   * @return the selected URI.
   */
  public abstract String getUri(List<String> uris);

  /**
   * Return the server configuration.
   *
   * @return the server configuration.
   */
  public ServerConfig getConfig() {
    return config;
  }

  /**
   * Check a path is valid and available.
   *
   * @param path the path to check
   * @return if the path is valid and available, return {@code true}, otherwise {@code false}.
   */
  protected static boolean isAvailable(String path) {
    if (path == null) {
      throw new IllegalArgumentException("URI cannot be null");
    }

    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpGet httpGet = new HttpGet(path);
      httpGet.setHeader("Content-Type", "application/json");
      httpGet.setHeader("Accept", "application/json");
      try {
        LOG.info("trying to execute HTTP GET request to " + path);
        int code = httpClient.execute(httpGet, HttpResponse::getCode);
        return isSuccessful(code);
      } catch (Exception e) {
        return false;
      }
    } catch (IOException e) {
      return false;
    }
  }

  private static boolean isSuccessful(int code) {
    return code == HttpStatus.SC_OK
        || code == HttpStatus.SC_ACCEPTED
        || code == HttpStatus.SC_NO_CONTENT;
  }

  /**
   * Default implementation of {@link BaseUriSelector}. It selects a URI from a list of URIs in a
   * round-robin fashion.
   */
  static final class DefaultUriSelector extends BaseUriSelector {
    private AtomicInteger lastIndex = new AtomicInteger(-1);

    public DefaultUriSelector(ServerConfig config) {
      super(config);
    }

    @Override
    public String getUri(List<String> uris) {
      int size = uris.size();
      int retries = config.get(Configs.CLIENT_CONNECTION_RETRIES);
      int retryDelaySeconds = config.get(Configs.CLIENT_CONNECT_RETRY_DELAY);

      for (int attempt = 0; attempt < retries; ++attempt) {
        for (int i = 0; i < size; i++) {
          int currentIndex = (lastIndex.incrementAndGet() % size);
          if (currentIndex < 0) {
            lastIndex.set(0);
            currentIndex = 0;
          }

          String uri = uris.get(currentIndex);
          if (isAvailable(uri + "/" + CONFIG_API)) {
            return uri;
          }
        }

        if (retryDelaySeconds > 0) {
          LOG.info("Waiting " + retryDelaySeconds + " seconds before next connection attempt.");
          try {
            Thread.sleep(retryDelaySeconds * 1000L);
          } catch (InterruptedException e) {
            LOG.info("Interrupted while waiting for retry delay.");
          }
        }
      }

      throw new IllegalArgumentException("No available URI found from: " + uris);
    }
  }

  /**
   * Random implementation of {@link BaseUriSelector}. It selects a URI from a list of URIs
   * randomly.
   */
  static final class RandomUriSelector extends BaseUriSelector {

    /**
     * Construct a new instance of {@link BaseUriSelector}.
     *
     * @param config the server configuration.
     */
    public RandomUriSelector(ServerConfig config) {
      super(config);
    }

    @Override
    public String getUri(List<String> uris) {
      int size = uris.size();
      Set<Integer> unavailableIndexes = new HashSet<>();
      int retries = config.get(Configs.CLIENT_CONNECTION_RETRIES);
      int retryDelaySeconds = config.get(Configs.CLIENT_CONNECT_RETRY_DELAY);
      Random random = new Random();
      int index;

      for (int attempt = 0; attempt < retries; ++attempt) {
        while (unavailableIndexes.contains(index = random.nextInt(size))) {
          index = random.nextInt(size);
        }
        String uri = uris.get(index);
        if (isAvailable(uri + "/" + CONFIG_API)) {
          return uri;
        } else {
          unavailableIndexes.add(index);
        }

        if (retryDelaySeconds > 0) {
          LOG.info("Waiting " + retryDelaySeconds + " seconds before next connection attempt.");
          try {
            Thread.sleep(retryDelaySeconds * 1000L);
          } catch (InterruptedException e) {
            LOG.info("Interrupted while waiting for retry delay.");
          }
        }
      }

      throw new IllegalStateException("No available URI found from: " + uris);
    }
  }
}
