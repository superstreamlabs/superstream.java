package superstream;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

public class SuperstreamDeserializer<T> implements Deserializer<T>{
    private Deserializer<T> originalDeserializer;
    private SuperstreamConnection superstreamConnection;
    

    public SuperstreamDeserializer() {
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        try {
            String token  = configs.get("superstream.token")!= null ? (String) configs.get("superstream.token") : null;
            if (token == null) {
                    throw new IllegalArgumentException("superstream: error initializing superstream:: token is required");
            }
            String superstreamHost = configs.get("superstream.host")!= null ? (String) configs.get("superstream.host") : "broker.superstream.dev";
            if (superstreamHost == null) {
                superstreamHost = Consts.superstreamDefaultHost;
            }
            int learningFactor = configs.get("superstream.learning.factor")!= null ? (Integer) configs.get("superstream.learning.factor") : 20;
            String originalDeserializerClassName = configs.get(Consts.originalDeserializer)!= null ? (String) configs.get(Consts.originalDeserializer) : null;
            if (originalDeserializerClassName == null) {
                throw new IllegalArgumentException("superstream: error initializing superstream: original serializer is required");
            }
            Class<?> originalDeserializerClass = Class.forName(originalDeserializerClassName);
            originalDeserializer = (Deserializer<T>) originalDeserializerClass.getDeclaredConstructor().newInstance();
            originalDeserializer.configure(configs, isKey);
            SuperstreamConnection superstreamConn = new SuperstreamConnection(token, superstreamHost, learningFactor, false);
            superstreamConnection = superstreamConn;
            superstreamConnection.config = configs;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        byte[] dataToDesrialize;
        if (superstreamConnection != null && superstreamConnection.messageBuilder != null) {
            try {
                byte[] supertstreamDeserialized = superstreamConnection.protoToJson(data);
                dataToDesrialize = supertstreamDeserialized;
            } catch (Exception e) {
                dataToDesrialize = data;
                superstreamConnection.handleError(String.format("error deserializing data: ", e.getMessage()));
            }
        } else {
            dataToDesrialize = data;
        }
        T deserializedData = originalDeserializer.deserialize(topic, dataToDesrialize);
        return deserializedData;
    }

    @Override
    public T deserialize(String topic, Headers headers, byte[] data) {
        Header[] headersList = headers.toArray();
        for (Header header : headersList) {
            String headerKey = new String(header.key());
            if("superstream_schema".equals(headerKey)) {
                String schemaId = new String(header.value(), StandardCharsets.UTF_8);
                
            
            }
        }
        byte[] dataToDesrialize;
        if (superstreamConnection != null && superstreamConnection.messageBuilder != null) {
            try {
                byte[] supertstreamDeserialized = superstreamConnection.protoToJson(data);
                dataToDesrialize = supertstreamDeserialized;
            } catch (Exception e) {
                dataToDesrialize = data;
                superstreamConnection.handleError(String.format("error deserializing data: ", e.getMessage()));
            }
        } else {
            dataToDesrialize = data;
        }
        T deserializedData = originalDeserializer.deserialize(topic, dataToDesrialize);
        return deserializedData;
    }

    @Override
    public void close() {
        originalDeserializer.close();
        superstreamConnection.close();
    }
}
