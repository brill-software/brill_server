package brill.server.utils;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.junit.jupiter.MockitoExtension;

@RunWith(JUnitPlatform.class)
@ExtendWith(MockitoExtension.class)
public class TopicUtilsTest {

    @Test
    public void parseUri() throws Exception {
        URI uri = new URI("file:/dir/path/file.ext");
        System.out.println("scheme = " + uri.getScheme());
        System.out.println("path = " + uri.getPath());
        System.out.println("host = " + uri.getHost());
    }  


    @Test
    public void parseUri2() throws Exception {
        URI uri = new URI("/dir/path/file.ext");
        System.out.println("scheme = " + uri.getScheme());
        System.out.println("path = " + uri.getPath());
        System.out.println("host = " + uri.getHost());
    } 
}