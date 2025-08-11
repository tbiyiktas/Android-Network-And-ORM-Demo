package lib.net.util;

import java.util.HashMap;

import lib.net.command.ACommand;

public class UrlBuilder {

    // Hatanın düzeltildiği satır
    public static String build(String basePath, ACommand command) {
        StringBuilder urlBuilder = new StringBuilder(basePath);

        String relativeUrl = command.getRelativeUrl();
        if (relativeUrl != null && !relativeUrl.isEmpty()) {
            urlBuilder.append(relativeUrl);
        }

        HashMap<String, String> queryParams = command.getQueryParams();
        if (queryParams != null && !queryParams.isEmpty()) {
            urlBuilder.append("?");
            boolean firstParam = true;
            for (String key : queryParams.keySet()) {
                if (!firstParam) {
                    urlBuilder.append("&");
                }
                urlBuilder.append(key).append("=").append(queryParams.get(key));
                firstParam = false;
            }
        }

        return urlBuilder.toString();
    }
}