/*
 * The MIT License
 *
 * Copyright (c) 2016-2017 Marcelo "Ataxexe" Guimarães
 * <ataxexe@devnull.tools>
 *
 * ----------------------------------------------------------------------
 * Permission  is hereby granted, free of charge, to any person obtaining
 * a  copy  of  this  software  and  associated  documentation files (the
 * "Software"),  to  deal  in the Software without restriction, including
 * without  limitation  the  rights to use, copy, modify, merge, publish,
 * distribute,  sublicense,  and/or  sell  copies of the Software, and to
 * permit  persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The  above  copyright  notice  and  this  permission  notice  shall be
 * included  in  all  copies  or  substantial  portions  of the Software.
 *                        -----------------------
 * THE  SOFTWARE  IS  PROVIDED  "AS  IS",  WITHOUT  WARRANTY OF ANY KIND,
 * EXPRESS  OR  IMPLIED,  INCLUDING  BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN  NO  EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM,  DAMAGES  OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT  OR  OTHERWISE,  ARISING  FROM,  OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE   OR   THE   USE   OR   OTHER   DEALINGS  IN  THE  SOFTWARE.
 */
package tools.devnull.jenkins.plugins.buildnotifications;

import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NTCredentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

/**
 * Factory that builds an {@link HttpClient} honouring the global proxy
 * configuration of Jenkins ({@link ProxyConfiguration}).
 *
 * <p>Before this class existed each notifier created a bare
 * {@code new HttpClient()}, which silently ignored the proxy configured in
 * Jenkins &rarr; global configuration &rarr; "HTTP Proxy", so notification
 * requests failed on installations that reach the internet only through a
 * proxy.</p>
 *
 * <p>Usage: replace {@code new HttpClient()} with
 * {@code NotifierHttpClient.create(targetUrl)}.</p>
 */
public final class NotifierHttpClient {

  private static final Logger LOGGER = Logger.getLogger(NotifierHttpClient.class.getName());

  private NotifierHttpClient() {
    // no instances
  }

  /**
   * Creates an {@link HttpClient} that routes through the Jenkins proxy when
   * one is configured and the target host is not listed in the proxy
   * no-proxy (exclusion) list.
   *
   * @param targetUrl the URL the client is going to talk to (used to honour
   *                  the proxy no-proxy exclusion list); may be {@code null}
   * @return a pre-configured {@link HttpClient}
   */
  public static HttpClient create(String targetUrl) {
    HttpClient client = new HttpClient();

    Jenkins jenkins = Jenkins.getInstance();
    if (jenkins == null) {
      return client;
    }

    ProxyConfiguration proxy = jenkins.proxy;
    if (proxy == null) {
      return client;
    }

    String name = proxy.name;
    if (name == null || name.trim().isEmpty()) {
      return client;
    }

    String host = extractHost(targetUrl);
    if (shouldBypass(proxy.noProxyHost, host)) {
      return client;
    }

    // Mirror what jenkins-core itself does (hudson.ProxyConfiguration):
    // route the request through the configured HTTP proxy.
    String proxyHost = name.trim();
    client.getHostConfiguration().setProxy(proxyHost, proxy.port);

    // Apply proxy authentication when credentials are configured, otherwise the
    // proxy answers "407 Proxy Authentication Required" (e.g. Tinyproxy) and the
    // request is dropped before it ever reaches Telegram.
    String user = proxy.getUserName();
    if (user != null && !user.trim().isEmpty()) {
      client.getState().setProxyCredentials(
          new AuthScope(proxyHost, proxy.port),
          createProxyCredentials(user, proxy.getPassword()));
    }

    LOGGER.fine("Build Notifications: using proxy " + proxyHost + ":" + proxy.port
        + (user != null && !user.trim().isEmpty() ? " (authenticated)" : "")
        + " for host " + (host == null ? "<unknown>" : host));
    return client;
  }

  /**
   * Builds the proxy {@link Credentials} for the given user name and password,
   * following the same rules as jenkins-core
   * ({@code hudson.ProxyConfiguration#createCredentials}): when the user name
   * contains a backslash it is treated as the NTLM form, otherwise a plain
   * basic-auth credential is used.
   */
  private static Credentials createProxyCredentials(String userName, String password) {
    int sep = userName.indexOf('\\');
    if (sep >= 0) {
      String domain = userName.substring(0, sep);
      String user = userName.substring(sep + 1);
      return new NTCredentials(user, password, domain, "");
    }
    return new UsernamePasswordCredentials(userName, password);
  }

  /**
   * Returns the host part of the given URL, or {@code null} if it cannot be
   * determined.
   */
  private static String extractHost(String url) {
    if (url == null) {
      return null;
    }
    try {
      return new URL(url).getHost();
    } catch (MalformedURLException e) {
      return null;
    }
  }

  /**
   * Checks whether the given host is covered by the proxy no-proxy list.
   *
   * <p>The no-proxy list may use {@code ,} or {@code |} as separators and
   * supports a leading {@code *} wildcard (e.g. {@code *.example.com}). A
   * bare entry matches the host itself or any subdomain of it.</p>
   */
  private static boolean shouldBypass(String noProxyHost, String host) {
    if (noProxyHost == null || noProxyHost.isEmpty() || host == null) {
      return false;
    }
    for (String pattern : noProxyHost.split("[|,]")) {
      pattern = pattern.trim();
      if (pattern.isEmpty()) {
        continue;
      }
      if (pattern.startsWith("*")) {
        String suffix = pattern.substring(1);
        if (host.endsWith(suffix)) {
          return true;
        }
      } else if (host.equals(pattern) || host.endsWith("." + pattern)) {
        return true;
      }
    }
    return false;
  }

}
