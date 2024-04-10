package superstream;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.kafka.common.serialization.Deserializer;

import com.google.protobuf.Descriptors;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

public class SuperstreamDeserializer<T> implements Deserializer<T>{
    private Deserializer<T> originalDeserializer;
    private Superstream superstreamConnection;
    

    public SuperstreamDeserializer() {
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        try {
            String token  = configs.get(Consts.superstreamTokenKey)!= null ? (String) configs.get(Consts.superstreamTokenKey) : null;
            if (token == null) {
                throw new Exception("token is required");
            }
            String superstreamHost = configs.get(Consts.superstreamHostKey)!= null ? (String) configs.get(Consts.superstreamHostKey) : Consts.superstreamDefaultHost;
            if (superstreamHost == null) {
                superstreamHost = Consts.superstreamDefaultHost;
            }
            int learningFactor = configs.get(Consts.superstreamLearningFactorKey)!= null ? (Integer) configs.get(Consts.superstreamLearningFactorKey) : Consts.superstreamDefaultLearningFactor;
            String originalDeserializerClassName = configs.get(Consts.originalDeserializer)!= null ? (String) configs.get(Consts.originalDeserializer) : null;
            if (originalDeserializerClassName == null) {
                throw new Exception("original deserializer is required");
            }
            Class<?> originalDeserializerClass = Class.forName(originalDeserializerClassName);
            @SuppressWarnings("unchecked")
            Deserializer<T> originalDeserializerT = (Deserializer<T>) originalDeserializerClass.getDeclaredConstructor().newInstance();
            originalDeserializer = originalDeserializerT;
            originalDeserializer.configure(configs, isKey);
            Superstream superstreamConn = new Superstream(token, superstreamHost, learningFactor, "consumer", configs);
            superstreamConnection = superstreamConn;
            superstreamConnection.config = configs;
        } catch (Exception e) {
            String errMsg = String.format("superstream: error initializing superstream: %s", e.getMessage());
            if (superstreamConnection != null) {
                superstreamConnection.handleError(errMsg);
            }
            System.out.println(errMsg);
        }
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        T deserializedData = originalDeserializer.deserialize(topic, data);
        return deserializedData;
    }

    @Override
    public T deserialize(String topic, Headers headers, byte[] data) {
        String schemaId = null;
        byte[] dataToDesrialize = data;
        if (superstreamConnection != null){
            superstreamConnection.clientCounters.incrementTotalBytesAfterReduction(data.length);
        }
        Header header = headers.lastHeader("superstream_schema");
        if (header != null) {
            schemaId = new String(header.value(), StandardCharsets.UTF_8);
        }
        if (schemaId != null) {
            Descriptors.Descriptor desc = superstreamConnection.SchemaIDMap.get(schemaId);
            if (desc == null){
                superstreamConnection.sendGetSchemaRequest(schemaId);
                desc = superstreamConnection.SchemaIDMap.get(schemaId);
                if (desc == null) {
                    superstreamConnection.handleError("error getting schema with id: " + schemaId);
                    System.out.println("superstream: shcema not found");
                    return null;
                }
            }
            try {
                byte[] supertstreamDeserialized = superstreamConnection.protoToJson(data, desc);
                dataToDesrialize = supertstreamDeserialized;
                superstreamConnection.clientCounters.incrementTotalBytesBeforeReduction(supertstreamDeserialized.length);
                superstreamConnection.clientCounters.incrementTotalMessagesSuccessfullyConsumed();
            } catch (Exception e) {
                superstreamConnection.handleError(String.format("error deserializing data: %s", e.getMessage()));
                return null;
            }
        } else {
            superstreamConnection.clientCounters.incrementTotalBytesBeforeReduction(data.length);
            superstreamConnection.clientCounters.incrementTotalMessagesFailedConsume();
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
