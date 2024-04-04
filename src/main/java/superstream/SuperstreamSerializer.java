package superstream;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.Serializer;

public class SuperstreamSerializer<T> implements Serializer<T>{
    private Serializer<T> originalSerializer;
    private Superstream superstreamConnection;

    public SuperstreamSerializer() {
    }
    
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        try {
            String token  = configs.get("superstream.token")!= null ? (String) configs.get("superstream.token") : null;
            if (token == null) {
                throw new Exception("token is required");
            }
            String superstreamHost = configs.get("superstream.host")!= null ? (String) configs.get("superstream.host") : "broker.superstream.dev";
            if (superstreamHost == null) {
                superstreamHost = Consts.superstreamDefaultHost;
            }
            int learningFactor = configs.get("superstream.learning.factor")!= null ? (Integer) configs.get("superstream.learning.factor") : 20;
            String originalSerializerClassName = configs.get(Consts.originalSerializer)!= null ? (String) configs.get(Consts.originalSerializer) : null;
            if (originalSerializerClassName == null) {
                throw new Exception("original serializer is required");
            }
            try {
                Class<?> originalSerializerClass = Class.forName(originalSerializerClassName);
                originalSerializer = (Serializer<T>) originalSerializerClass.getDeclaredConstructor().newInstance();
                originalSerializer.configure(configs, isKey);
                Superstream superstreamConn = new Superstream(token, superstreamHost, learningFactor, "producer", configs);
                superstreamConnection = superstreamConn;
                superstreamConnection.config = configs;
            } catch (Exception e) {
                throw e;
            }
        } catch (Exception e) {
            String errMsg = String.format("superstream: error initializing superstream: %s", e.getMessage());
            if (superstreamConnection != null) {
                superstreamConnection.handleError(errMsg);
            }
            System.out.println(errMsg);
        }
    }

    @Override
    public byte[] serialize(String topic, T data) {
        byte[] serializedData = originalSerializer.serialize(topic, data);
        return serializedData;
    }

    @Override
    public byte[] serialize(String topic, Headers headers, T data) {
        byte[] serializedData = originalSerializer.serialize(topic, data);
        byte[] serializedResult;
        if (superstreamConnection != null) {
            if (superstreamConnection.descriptor != null){
                try {
                    Header header = new RecordHeader("superstream_schema",  superstreamConnection.ProducerSchemaID.getBytes(StandardCharsets.UTF_8));
                    headers.add(header);
                    byte[] superstreamSerialized = superstreamConnection.jsonToProto(serializedData);
                    superstreamConnection.clientCounters.incrementTotalBytesBeforeReduction(serializedData.length);
                    superstreamConnection.clientCounters.incrementTotalBytesAfterReduction(superstreamSerialized.length);
                    superstreamConnection.clientCounters.incrementTotalMessagesSuccessfullyProduce();
                    return superstreamSerialized;
                } catch (Exception e) {
                    serializedResult = serializedData;
                    superstreamConnection.handleError(String.format("error serializing data: ", e.getMessage()));
                    superstreamConnection.clientCounters.incrementTotalBytesAfterReduction(serializedData.length);
                    superstreamConnection.clientCounters.incrementTotalMessagesFailedProduce();
                }
            } else {
                serializedResult = serializedData;
                superstreamConnection.clientCounters.incrementTotalBytesAfterReduction(serializedData.length);
                if (superstreamConnection.learningFactorCounter <= superstreamConnection.learningFactor) {
                    superstreamConnection.sendLearningMessage(serializedResult);
                    superstreamConnection.learningFactorCounter++;
                } else if (!superstreamConnection.learningRequestSent) {
                    superstreamConnection.sendRegisterSchemaReq();
                }
            }
        } else {
            serializedResult = serializedData;
        }
        return serializedResult;
    }

    @Override
    public void close() {
        originalSerializer.close();
        if (superstreamConnection != null){
            superstreamConnection.close();
        }
    }
}