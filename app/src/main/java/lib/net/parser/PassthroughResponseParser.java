package lib.net.parser;

import java.lang.reflect.Type;

public class PassthroughResponseParser implements IResponseParser {
    @Override
    public <T> T parse(String body, Type type) {
        if (type == String.class) {
            @SuppressWarnings("unchecked")
            T casted = (T) (body == null ? "" : body);
            return casted; // hiçbir dokunuş yok
        }
        throw new IllegalStateException(
                "PassthroughResponseParser only supports String.class. Requested: " + type
        );
    }
}
