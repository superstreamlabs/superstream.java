package ai.superstream;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.kafka.common.serialization.Deserializer;

import com.google.protobuf.Descriptors;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

public class SuperstreamDeserializer<T> implements Deserializer<T> {
    private Deserializer<T> originalDeserializer;
    private Superstream superstreamConnection;

    public SuperstreamDeserializer() {
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        try {
            Object originalDeserializerObj = configs.get(Consts.originalDeserializer);
            if (originalDeserializerObj == null) {
                throw new Exception("original deserializer is required");
            }
            Class<?> originalDeserializerClass;

            if (originalDeserializerObj instanceof String) {
                originalDeserializerClass = Class.forName((String) originalDeserializerObj);
            } else if (originalDeserializerObj instanceof Class) {
                originalDeserializerClass = (Class<?>) originalDeserializerObj;
            } else {
                throw new Exception("Invalid type for original deserializer");
            }
            @SuppressWarnings("unchecked")
            Deserializer<T> originalDeserializerT = (Deserializer<T>) originalDeserializerClass.getDeclaredConstructor()
                    .newInstance();
            this.originalDeserializer = originalDeserializerT;
            this.originalDeserializer.configure(configs, isKey);
            Superstream superstreamConn = (Superstream) configs.get(Consts.superstreamConnectionKey);
            if (superstreamConn == null) {
                System.out.println("Failed to connect to Superstream");
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
    public T deserialize(String topic, byte[] data) {
        if (originalDeserializer == null) {
            return null;
        }
        T deserializedData = originalDeserializer.deserialize(topic, data);
        return deserializedData;
    }

    @Override
    public T deserialize(String topic, Headers headers, byte[] data) {
        if (originalDeserializer == null) {
            return null;
        }
        String schemaId = null;
        Header header = headers.lastHeader("superstream_schema");
        if (header != null) {
            schemaId = new String(header.value(), StandardCharsets.UTF_8);
        }
        byte[] dataToDesrialize = data;
        if (dataToDesrialize == null) {
            this.originalDeserializer.deserialize(topic, headers, dataToDesrialize);
        }

        
        if (this.superstreamConnection != null) {
            this.superstreamConnection.clientCounters.incrementTotalBytesAfterReduction(data.length);
        }
        
        if (schemaId != null) {
            if (!this.superstreamConnection.superstreamReady) {
                int totalWaitTime = 60;
                int checkInterval = 5;  
                try {
                    for (int i = 0; i < totalWaitTime; i += checkInterval) {
                        if (this.superstreamConnection.superstreamReady) {
                            break;
                        }
                        Thread.sleep(checkInterval * 1000);
                    }
                } catch (Exception e) {}
            }
            if (!this.superstreamConnection.superstreamReady) {
                System.out.println("superstream: cannot connect with superstream and consume message that was modified by superstream");
                return null;
            }
            Descriptors.Descriptor desc = superstreamConnection.SchemaIDMap.get(schemaId);
            if (desc == null) {
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
                superstreamConnection.clientCounters
                        .incrementTotalBytesBeforeReduction(supertstreamDeserialized.length);
                superstreamConnection.clientCounters.incrementTotalMessagesSuccessfullyConsumed();
            } catch (Exception e) {
                superstreamConnection.handleError(String.format("error deserializing data: %s", e.getMessage()));
                return null;
            }
        } else {
            if (superstreamConnection != null) {
                superstreamConnection.clientCounters.incrementTotalBytesBeforeReduction(data.length);
                superstreamConnection.clientCounters.incrementTotalMessagesFailedConsume();
            }
        }
        T deserializedData = this.originalDeserializer.deserialize(topic, headers, dataToDesrialize);
        return deserializedData;
    }

    @Override
    public void close() {
        if (originalDeserializer != null) {
            originalDeserializer.close();
        }
        if (superstreamConnection != null) {
            superstreamConnection.close();
        }
    }
}
