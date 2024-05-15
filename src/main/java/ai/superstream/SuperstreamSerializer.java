package ai.superstream;

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
            System.out.println("Running Superstream Kafka Producer");
            String originalSerializerClassName = (String) configs.get(Consts.originalSerializer);
            if (originalSerializerClassName == null) {
                throw new Exception("original serializer is required");
            }
            Class<?> originalSerializerClass = Class.forName(originalSerializerClassName);
            @SuppressWarnings("unchecked")
            Serializer<T> originalSerializerT = (Serializer<T>) originalSerializerClass.getDeclaredConstructor().newInstance();
            this.originalSerializer = originalSerializerT;
            this.originalSerializer.configure(configs, isKey);
            Superstream superstreamConn = (Superstream) configs.get(Consts.superstreamConnectionKey);
            if (superstreamConn == null) {
                System.out.println("Failed to connect to Superstream - Running Kafka Producer");
            } else {
                this.superstreamConnection = superstreamConn;
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
        if (originalSerializer == null) {
            return null;
        }
        byte[] serializedData = originalSerializer.serialize(topic, data);
        return serializedData;
    }

    @Override
    public byte[] serialize(String topic, Headers headers, T data) {
        if (originalSerializer == null) {
            return null;
        }
        byte[] serializedData = this.originalSerializer.serialize(topic, data);
        byte[] serializedResult;
        if (superstreamConnection != null)  {
            if (superstreamConnection.reductionEnabled == true) {
                if (superstreamConnection.descriptor != null){
                    try {
                        Header header = new RecordHeader("superstream_schema",  superstreamConnection.ProducerSchemaID.getBytes(StandardCharsets.UTF_8));
                        headers.add(header);
                        byte[] superstreamSerialized = superstreamConnection.jsonToProto(serializedData);
                        superstreamConnection.clientCounters.incrementTotalBytesBeforeReduction(serializedData.length);
                        superstreamConnection.clientCounters.incrementTotalBytesAfterReduction(superstreamSerialized.length);
                        superstreamConnection.clientCounters.incrementTotalMessagesSuccessfullyProduce();
                        serializedResult = superstreamSerialized;
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
                superstreamConnection.clientCounters.incrementTotalBytesBeforeReduction(serializedData.length);
                superstreamConnection.clientCounters.incrementTotalMessagesFailedProduce();
            }
        } else {
            serializedResult = serializedData;
        }
        return serializedResult;
    }

    @Override
    public void close() {
        if (this.originalSerializer != null){
            originalSerializer.close();
        }
        if (superstreamConnection != null){
            superstreamConnection.close();
        }
    }
}
