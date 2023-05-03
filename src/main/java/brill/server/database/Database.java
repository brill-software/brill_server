// © 2005 Brill Software Limited - Brill Framework, distributed under the MIT license.
package brill.server.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Vector;

/**
 * Database connection pooling class.
 * 
 *  - A pool of connections is maintained, thus reducing the number of DB logins.
 *  - The executeQuery method returns the results as JSON.
 *  - Connections that are not returned to the pool within 5 minutes are automatically closed.
 * 
 */
public class Database {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Database.class);

    private String defaultDriver = "";
    private String defaultUrl = "";
    private String defaultUsername = "";
    private String defaultPassword = "";

    private Vector<CachedConnection> cache;
    private Thread monitor = null;
    private boolean stopMonitor = false;
    private long maxConnectionIdleTime = 1000 * 60 * 30; // 30 minutes
    private long maxInUseTime = 1000 * 60 * 10; // 10 minutes

    public Database() {
        cache = new Vector<CachedConnection>();
    }

    public Database(String defaultDriver, String defaultUrl, String defaultUsername, String defaultPassword) {
        this.defaultDriver = defaultDriver;
        this.defaultUrl = defaultUrl;
        this.defaultUsername = defaultUsername;
        this.defaultPassword = defaultPassword;
        cache = new Vector<CachedConnection>();
    }

    /**
     * Gets a database connection. A cache is maintained of database connections. An unsued connection in the cache is returned
     * if one is available, otherwise a new connection is obtained.
     * 
     * NOTE: When finished with a connection it must be returned to the pool of available connections by calling the
     * close method on the connection. Failure to do so will result the Monitor closing the connection after 5 minutes.
     * 
     * @param driver
     * @param url
     * @param username
     * @param password
     * @return
     */
    public CachedConnection getConnection(String driver, String url, String username, String password) {
        CachedConnection cachedConn = null;
        cachedConn = getExistingConnection(url,username,password);
        
        if (cachedConn != null)
            return cachedConn;

        cachedConn = getNewConnection(driver,url,username,password);
        return cachedConn;
    }

    /**
     * Gets a connection using the default connection details pased into the constructor.
     * 
     * @return A connection.
     */
    public CachedConnection getConnection() {
        return getConnection(defaultDriver, defaultUrl, defaultUsername, defaultPassword);
    }

    private synchronized CachedConnection getExistingConnection(String url, String username, String password) {
        CachedConnection cachedConn;

        for (int i=0; i < cache.size(); i++) {
            cachedConn = (CachedConnection)cache.get(i);

            if (!cachedConn.isInUse() && cachedConn.equals(url,username,password)) {
                cachedConn.setInUse(true);
                return cachedConn;
            }
        }
        return null;  // None found
    }

    private CachedConnection getNewConnection(String driver, String url,
                                             String username, String password) {
        // Note that only the section which updates the cache is synchronized. This
        // allows other cached and new connections to be obtained in parallel.
        CachedConnection cachedConnection = null;
        Connection conn = null;
        try {
            Class.forName(driver);
            conn = DriverManager.getConnection(url,username,password);
            cachedConnection = new CachedConnection(conn,url,username,password);

            synchronized(this) {
                cache.add(cachedConnection);
                if (monitor == null) {
                    monitor = new DatabaseMonitor(this);
                    monitor.start();
                }
            }
        } catch (ClassNotFoundException cnf) {
            log.error("Can't load database driver: " + cnf.getMessage());
        } catch (SQLException se) {
            log.error("SQL Exception: " + se.getMessage());
        }

        return cachedConnection;
    }

    synchronized void checkUse() {
        CachedConnection cachedConnection;
        long timeNow = System.currentTimeMillis();
        int numConnections = cache.size();

        for (int i = numConnections - 1; i >= 0; i--) {
            cachedConnection = (CachedConnection)cache.get(i);
            long lastInUseChange = cachedConnection.getLastInUseChange();

            if (cachedConnection.isInUse()) {
                if (timeNow - lastInUseChange > maxInUseTime)
                {
                    log.error("Database connection in use for too long. URL=" +
                              cachedConnection.getUrl() + " Username=" + cachedConnection.getUsername());
                    cachedConnection.closeConnection();
                    cache.remove(i);
                }
            }
            else {
                if (timeNow - lastInUseChange > maxConnectionIdleTime) {
                    cachedConnection.closeConnection();
                    cache.remove(i);
                }
            }
        }
    }

    boolean getStopMonitor() {
        return stopMonitor;
    }

    public void finalize() {
        CachedConnection cachedConnection;
        stopMonitor = true;
        int numConnections = cache.size();

        for (int i = 0; i < numConnections; i++) {
            cachedConnection = (CachedConnection)cache.get(i);
            cachedConnection.closeConnection();
            cache.remove(i);
        }
    }
}