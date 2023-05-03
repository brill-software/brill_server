package brill.server.utils;

import static org.junit.Assert.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.junit.jupiter.MockitoExtension;

@RunWith(JUnitPlatform.class)
@ExtendWith(MockitoExtension.class)
public class SqlUtilsTest {
    
    @Test
    public void inlineDashDashComment() throws Exception {
        String sql = "select * from employee -- Comment\n";
        String result = SqlUtils.stripComments(sql);
        assertEquals(result, "select * from employee \n");
    }

    @Test
    public void inlineHashComment() throws Exception {
        String sql = "select * from employee # Comment\n";
        String result = SqlUtils.stripComments(sql);
        assertEquals(result, "select * from employee \n");
    }

    @Test
    public void multiLineComment() throws Exception {
        String sql = "\n\n/*\n * Comment\n*/\nselect * from employee\n\n";
        String result = SqlUtils.stripComments(sql);
        assertEquals(result, "select * from employee\n");
    }

    @Test
    public void inlineStarSlashComment() throws Exception {
        String sql = "select * from /* comment */ employee\n";
        String result = SqlUtils.stripComments(sql);
        assertEquals(result, "select * from  employee\n");
    }

    @Test
    public void quotedString() throws Exception {
        String sql = "select * from employee where field = '/* NOT A COMMENT */'\n";
        String result = SqlUtils.stripComments(sql);
        assertEquals(result, "select * from employee where field = '/* NOT A COMMENT */'\n");
    }

    @Test
    public void doubleQuotedString() throws Exception {
        String sql = "select * from employee where field = \"# NOT A COMMENT \"\n";
        String result = SqlUtils.stripComments(sql);
        assertEquals(result, "select * from employee where field = \"# NOT A COMMENT \"\n");
    }
    
    @Test
    public void everything() throws Exception {
        String sql = "\n\n/*\n * Comment\n */\n-- Comment\n# Comment\nselect * from /* Comment */ employee where field = \"# NOT A COMMENT \"\n# Comment\n";
        String result = SqlUtils.stripComments(sql);
        assertEquals(result, "select * from  employee where field = \"# NOT A COMMENT \"\n");
    }
}