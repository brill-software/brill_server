package brill.server.service;

import static org.junit.Assert.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.junit.jupiter.MockitoExtension;

@RunWith(JUnitPlatform.class)
@ExtendWith(MockitoExtension.class)
public class JavaScriptServiceTest {

    JavaScriptService service = null;

    @BeforeEach
    void setUp() {
        service = new JavaScriptService();
    }

    @Disabled
    @Test
    public void exectureHelloWorld() throws Exception {
        System.out.println("Running hello world script");
        String result = service.execute("print('Hello there'); {result: 1};","", "{'test': 1}", "testuser", false);
        assertTrue(result.contains("result"));
    }
}