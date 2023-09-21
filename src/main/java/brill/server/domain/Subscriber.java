// © 2021 Brill Software Limited - Brill Middleware, distributed under the MIT License.
package brill.server.domain;

import javax.json.JsonValue;
import org.springframework.web.socket.WebSocketSession;

import brill.server.utils.JsonUtils;

public class Subscriber {
    private WebSocketSession session;
    private String filter;

    public Subscriber(WebSocketSession session, String filter) {
        this.session = session;
        this.filter = filter;
    }

    public WebSocketSession getSession() {
        return session;
    }

    public String getFilter() {
        return filter;
    }

    public JsonValue getFilterJsonValue() {
        JsonValue filterValue = JsonUtils.jsonValueFromString(filter);
        return filterValue;
    }

}