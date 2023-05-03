// © 2005 Brill Software Limited - Brill Framework, distributed under the MIT license.
package brill.server.database;

/**
 * Monitors the database connections.
 */
class DatabaseMonitor extends Thread {

    private Database database = null;
    private static int SLEEP_TIME = 1000 * 60 * 5; // 5 minutes

    public DatabaseMonitor(Database database) {
        this.database = database;
    }

    public void run() {
        while (!database.getStopMonitor()) {
            try {
                sleep(SLEEP_TIME);
                database.checkUse();
            }
            catch (InterruptedException ignore) {
            }
        }
    }
}