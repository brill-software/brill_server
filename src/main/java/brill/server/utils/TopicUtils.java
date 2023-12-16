package brill.server.utils;

public class TopicUtils {

    static public String getFileExtension(String topic) {
        int lastDotPos = topic.lastIndexOf(".");
        if (lastDotPos == -1) {
            return "";
        }        
        return topic.substring(lastDotPos + 1);
    }
}
