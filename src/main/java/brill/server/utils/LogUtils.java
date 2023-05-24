package brill.server.utils;

public class LogUtils {

    private static int MAX_LOG__ENTRY_LEN = 300;

    public static String truncate(String msg) {
        if (msg.length() < LogUtils.MAX_LOG__ENTRY_LEN) {
            return msg;
        }
        return msg.substring(0, LogUtils.MAX_LOG__ENTRY_LEN) + "...";
    }
}
