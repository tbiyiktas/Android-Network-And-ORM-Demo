package lib.net.command;

import java.util.HashMap;

public class GetCommand extends ACommand {
    public GetCommand(String relativeUrl) {
        super(relativeUrl);
    }

    public GetCommand(String relativeUrl, HashMap<String, String> parameters) {
        super(relativeUrl, parameters);
    }

    public GetCommand(String relativeUrl, HashMap<String, String> parameters, HashMap<String, String> headers) {
        super(relativeUrl, parameters, headers);
    }

    @Override
    public String getMethodName() {
        return "GET";
    }
}