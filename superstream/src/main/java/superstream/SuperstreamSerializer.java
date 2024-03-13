package superstream;

import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.Serializer;

public class SuperstreamSerializer<T> implements Serializer<T>{
    private Serializer<T> originalSerializer;
    private SuperstreamConnection superstreamConnection;

    public SuperstreamSerializer() {
    }
    
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        String token  = configs.get("superstream.token")!= null ? (String) configs.get("superstream.token") : null;
         if (token == null) {
                throw new IllegalArgumentException("superstream: error initializing superstream:: token is required");
        }
        String superstreamHost = configs.get("superstream.host")!= null ? (String) configs.get("superstream.host") : "broker.superstream.dev";
        if (superstreamHost == null) {
            superstreamHost = Consts.superstreamDefaultHost;
        }
        int learningFactor = configs.get("superstream.learning.factor")!= null ? (Integer) configs.get("superstream.learning.factor") : 20;
        String originalSerializerClassName = configs.get(Consts.originalSerializer)!= null ? (String) configs.get(Consts.originalSerializer) : null;
        if (originalSerializerClassName == null) {
            throw new IllegalArgumentException("superstream: error initializing superstream: original serializer is required");
        }
        try {
            Class<?> originalSerializerClass = Class.forName(originalSerializerClassName);
            originalSerializer = (Serializer<T>) originalSerializerClass.getDeclaredConstructor().newInstance();
            originalSerializer.configure(configs, isKey);
            SuperstreamConnection superstreamConn = new SuperstreamConnection(token, superstreamHost, learningFactor, true);
            superstreamConnection = superstreamConn;
            superstreamConnection.config = configs;
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public byte[] serialize(String topic, T data) {
        byte[] serializedData = originalSerializer.serialize(topic, data);
        byte[] serializedResult;
        if (superstreamConnection != null && superstreamConnection.messageBuilder != null){
            try {
                byte[] superstreamSerialized = superstreamConnection.jsonToProto(serializedData);
                serializedResult = superstreamSerialized;
                superstreamConnection.clientCounters.incrementTotalBytesAfterReduction(serializedData.length);
                superstreamConnection.clientCounters.incrementTotalMessagesSuccessfullyProduce();
            } catch (Exception e) {
                serializedResult = serializedData;
                superstreamConnection.handleError(String.format("error serializing data: ", e.getMessage()));
                superstreamConnection.clientCounters.incrementTotalBytesAfterReduction(serializedData.length);
                superstreamConnection.clientCounters.incrementTotalMessagesFailedProduce();
            }
        } else {
            serializedResult = serializedData;
            superstreamConnection.clientCounters.incrementTotalBytesAfterReduction(serializedData.length);
            superstreamConnection.clientCounters.incrementTotalMessagesFailedProduce();
            if (superstreamConnection.learningFactorCounter <= superstreamConnection.learningFactor) {
                superstreamConnection.learningFactorCounter++;
                superstreamConnection.sendLearningMessage(serializedResult);
            } else if (!superstreamConnection.learningRequestSent) {
                superstreamConnection.sendRegisterSchemaReq();
            }
        }
        return serializedResult;
    }

    @Override
    public byte[] serialize(String topic, Headers headers, T data) {
        byte[] serializedData = originalSerializer.serialize(topic, data);
        byte[] serializedResult;
        if (superstreamConnection.messageBuilder != null){
            try {
                Header header = new RecordHeader("superstream_schema", ByteBuffer.allocate(Integer.BYTES).putInt(superstreamConnection.ProducerSchemaID).array());
                headers.add(header);
                byte[] superstreamSerialized = superstreamConnection.jsonToProto(serializedData);
                serializedResult = superstreamSerialized;
                superstreamConnection.clientCounters.incrementTotalBytesAfterReduction(serializedData.length);
                superstreamConnection.clientCounters.incrementTotalMessagesSuccessfullyProduce();
            } catch (Exception e) {
                serializedResult = serializedData;
                superstreamConnection.handleError(String.format("error serializing data: ", e.getMessage()));
                superstreamConnection.clientCounters.incrementTotalBytesAfterReduction(serializedData.length);
                superstreamConnection.clientCounters.incrementTotalMessagesFailedProduce();
            }
        } else {
            serializedResult = serializedData;
            superstreamConnection.clientCounters.incrementTotalBytesAfterReduction(serializedData.length);
            superstreamConnection.clientCounters.incrementTotalMessagesFailedProduce();
            if (superstreamConnection.learningFactorCounter <= superstreamConnection.learningFactor) {
                superstreamConnection.learningFactorCounter++;
                superstreamConnection.sendLearningMessage(serializedResult);
            } else if (!superstreamConnection.learningRequestSent) {
                superstreamConnection.sendRegisterSchemaReq();
            }
        }
        return serializedResult;
    }

    @Override
    public void close() {
        originalSerializer.close();
        superstreamConnection.close();
    }
}
