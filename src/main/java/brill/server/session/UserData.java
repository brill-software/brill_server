// © 2021 Brill Software Limited - Brill Framework, distributed under the Brill Software Proprietry License.
package brill.server.session;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;
@Component
@SessionScope
public class UserData {
    private String username;
     
    public UserData() {
        username = "";
    }
    
    public String getUsername() {
        return username;
    }
}
