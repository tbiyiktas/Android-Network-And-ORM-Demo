package lib.net.command;

import java.util.HashMap;

public class DeleteCommand extends ACommand {
    public DeleteCommand(String relativeUrl) {
        super(relativeUrl);
    }

    public DeleteCommand(String relativeUrl, HashMap<String, String> headers) {
        super(relativeUrl, null, headers);
    }

    @Override
    public String getMethodName() {
        return "DELETE";
    }
}