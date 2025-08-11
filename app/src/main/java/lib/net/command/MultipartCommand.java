package lib.net.command;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import lib.net.connection.IHttpConnection;

public class MultipartCommand extends ACommand {

    private static final String BOUNDARY = "----WebKitFormBoundary" + System.currentTimeMillis();
    private final HashMap<String, String> formFields;
    private final HashMap<String, File> files;

    public MultipartCommand(String relativeUrl, HashMap<String, String> headers, HashMap<String, String> formFields, HashMap<String, File> files) {
        super(relativeUrl, null, headers);
        this.formFields = formFields != null ? formFields : new HashMap<>();
        this.files = files != null ? files : new HashMap<>();
    }

    @Override
    protected String getMethodName() {
        return "POST";
    }

    @Override
    protected String getContentType() {
        return "multipart/form-data; boundary=" + BOUNDARY;
    }

    @Override
    protected void handleRequest(IHttpConnection connection) throws IOException {
        super.handleRequest(connection);
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setUseCaches(false);
        writeMultipart(connection);
    }

    private void writeMultipart(IHttpConnection connection) throws IOException {
        try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {

            // Form verilerini yaz
            for (Map.Entry<String, String> entry : formFields.entrySet()) {
                writeBoundary(outputStream);
                writeFormField(outputStream, entry.getKey(), entry.getValue());
            }

            // Dosyaları yaz
            for (Map.Entry<String, File> entry : files.entrySet()) {
                writeBoundary(outputStream);
                writeFile(outputStream, entry.getKey(), entry.getValue());
            }

            // Son sınırı yaz
            outputStream.writeBytes("--" + BOUNDARY + "--\r\n");
            outputStream.flush();
        }
    }

    private void writeBoundary(DataOutputStream outputStream) throws IOException {
        outputStream.writeBytes("--" + BOUNDARY + "\r\n");
    }

    private void writeFormField(DataOutputStream outputStream, String name, String value) throws IOException {
        outputStream.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n");
        outputStream.writeBytes("Content-Type: text/plain; charset=utf-8\r\n");
        outputStream.writeBytes("\r\n");
        outputStream.write(value.getBytes(StandardCharsets.UTF_8));
        outputStream.writeBytes("\r\n");
    }

    private void writeFile(DataOutputStream outputStream, String name, File file) throws IOException {
        outputStream.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + file.getName() + "\"\r\n");
        outputStream.writeBytes("Content-Type: application/octet-stream\r\n");
        outputStream.writeBytes("Content-Transfer-Encoding: binary\r\n");
        outputStream.writeBytes("\r\n");

        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        outputStream.writeBytes("\r\n");
    }
}