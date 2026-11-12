package org.jmxmcp.jmx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMISocketFactory;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Helpers for opening JMX remote connections with bounded timeouts.
 *
 * The JMX remote API has no standard connect-timeout environment key, so
 * {@link #connect} bounds both phases of a connection:
 * <ul>
 *   <li>the JNDI/RMI registry lookup, via a JVM-wide default
 *       {@link RMISocketFactory} with a connect timeout (installed once), and</li>
 *   <li>the RMI connection itself, via the {@code jmx.remote.rmi.client.socket.factory}
 *       environment key.</li>
 * </ul>
 * Reads on an established connection are bounded by
 * {@code sun.rmi.transport.tcp.responseTimeout} (set once, JVM-wide).
 */
public final class JmxConnectors {

    private static final Logger logger = LoggerFactory.getLogger(JmxConnectors.class);

    /** Fallback timeout when none (or an invalid one) is configured. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private static boolean defaultSocketFactoryInstalled = false;

    private JmxConnectors() {
    }

    /**
     * Opens a JMX connection, bounding both the connect and read phases by
     * the given timeout. A null/zero/negative timeout falls back to
     * {@link #DEFAULT_TIMEOUT}.
     */
    public static JMXConnector connect(JMXServiceURL url, Map<String, Object> environment, Duration timeout)
            throws IOException {
        Duration effective = (timeout == null || timeout.isZero() || timeout.isNegative())
                ? DEFAULT_TIMEOUT
                : timeout;
        int timeoutMillis = (int) effective.toMillis();

        Map<String, Object> env = environment != null ? new HashMap<>(environment) : new HashMap<>();
        // Bounds the RMI connection to the RMIServer (after the registry lookup).
        env.put("jmx.remote.rmi.client.socket.factory", new TimeoutRmiClientSocketFactory(timeoutMillis));

        // Bounds the initial JNDI registry lookup (JVM-wide, can only be installed once).
        installDefaultSocketFactory(timeoutMillis);

        // Bound read hangs on established connections (JVM-wide RMI setting, set only if absent).
        if (System.getProperty("sun.rmi.transport.tcp.responseTimeout") == null) {
            System.setProperty("sun.rmi.transport.tcp.responseTimeout", String.valueOf(timeoutMillis));
        }

        return JMXConnectorFactory.connect(url, env);
    }

    private static synchronized void installDefaultSocketFactory(int connectTimeoutMillis) throws IOException {
        if (defaultSocketFactoryInstalled) {
            return;
        }
        try {
            RMISocketFactory.setSocketFactory(new RMISocketFactory() {
                @Override
                public Socket createSocket(String host, int port) throws IOException {
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
                    return socket;
                }

                @Override
                public ServerSocket createServerSocket(int port) throws IOException {
                    return new ServerSocket(port);
                }
            });
            defaultSocketFactoryInstalled = true;
        } catch (IOException e) {
            // Already set (e.g. by another component): the per-connection env
            // socket factory above still bounds the RMI connection itself.
            logger.debug("Could not install default RMI socket factory: {}", e.getMessage());
        }
    }

    /**
     * {@link RMIClientSocketFactory} that applies a connect timeout to each socket.
     */
    public static class TimeoutRmiClientSocketFactory implements RMIClientSocketFactory, Serializable {
        private static final long serialVersionUID = 1L;

        private final int connectTimeoutMillis;

        public TimeoutRmiClientSocketFactory(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
            return socket;
        }
    }
}
