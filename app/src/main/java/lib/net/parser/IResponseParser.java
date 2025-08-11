package lib.net.parser;

import java.lang.reflect.Type;

public interface IResponseParser {
    <T> T parse(String json, Type type);
}