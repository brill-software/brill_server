// © 2005 Brill Software Limited - Database Package, distributed under the MIT License.
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