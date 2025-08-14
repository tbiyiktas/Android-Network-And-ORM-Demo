package lib.net.util;
import java.util.HashMap;



import java.util.HashMap;

import lib.net.command.ACommand;

public class UrlBuilder {

    // Hatanın düzeltildiği satır
    public static String build(String basePath, ACommand command) {
        StringBuilder urlBuilder = new StringBuilder(basePath);
        String base = basePath == null ? "" : basePath;
        String relative = command != null ? command.getRelativeUrl() : null;

        // Normalize base and relative join (avoid double slashes)
        if (base.endsWith("/")) {
            urlBuilder.append(base.substring(0, base.length() - 1));
        } else {
            urlBuilder.append(base);
        }

        if (relative != null && !relative.isEmpty()) {
            if (!relative.startsWith("/")) {
                urlBuilder.append("/");
            }
            urlBuilder.append(relative);
        }


//        String relativeUrl = command.getRelativeUrl();
//        if (relativeUrl != null && !relativeUrl.isEmpty()) {
//            urlBuilder.append(relativeUrl);
//        }

        HashMap<String, String> queryParams = command.getQueryParams();
        if (queryParams != null && !queryParams.isEmpty()) {

            boolean hasQueryAlready = urlBuilder.indexOf("?") >= 0;
            urlBuilder.append(hasQueryAlready ? "&" : "?");

            //urlBuilder.append("?");

            boolean firstParam = true;
            for (String key : queryParams.keySet()) {
                if (!firstParam) {
                    urlBuilder.append("&");
                }

                String value = queryParams.get(key) == null ? "" : queryParams.get(key);

                //urlBuilder.append(key).append("=").append(queryParams.get(key));
                urlBuilder.append(key).append("=").append(value);
                firstParam = false;
            }
        }

        return urlBuilder.toString();
    }
}
