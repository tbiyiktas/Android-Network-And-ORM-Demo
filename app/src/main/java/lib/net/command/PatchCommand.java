package lib.net.command;

import java.io.IOException;
import java.util.HashMap;

import lib.net.connection.IHttpConnection;

public class PatchCommand extends ACommand {
    private final String jsonContent;

    public PatchCommand(String relativeUrl, String jsonContent, HashMap<String, String> headers) {
        super(relativeUrl, null, headers);
        this.jsonContent = jsonContent;
    }

    public PatchCommand(String relativeUrl, String jsonContent) {
        super(relativeUrl);
        this.jsonContent = jsonContent;
    }

    @Override
    public String getMethodName() {
        return "PATCH";
    }

    @Override
    protected void handleRequest(IHttpConnection connection) throws IOException {
        super.handleRequest(connection);
        connection.setDoOutput(true);
        write(connection, jsonContent);
    }
}