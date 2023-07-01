package brill.server.utils;

import java.io.File;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class DirUtils {

    private static long lastCleanUpTime = 0;

    public static void cleanUpOldFiles(String folderPath, int expirationPeriodHours) {
        // Only do a cleanup every hour.
        long currentTime = new Date().getTime();
        if ( currentTime - DirUtils.lastCleanUpTime < TimeUnit.HOURS.toMillis(1)) {
            return;
        }
        DirUtils.lastCleanUpTime = currentTime;

        File targetDir = new File(folderPath);
        if (!targetDir.exists()) {
            throw new RuntimeException(String.format("Log files directory '%s' " +
                    "does not exist in the environment", folderPath));
        }
    
        File[] files = targetDir.listFiles();
        for (File file : files) {
            long diff = currentTime - file.lastModified();
    
            // Granularity = DAYS;
            long desiredLifespan = TimeUnit.HOURS.toMillis(expirationPeriodHours); 
    
            if (diff > desiredLifespan) {
                file.delete();
            }
        }
    }
}
