package ai.superstream;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.Serializer;

import ai.superstream.Superstream.JsonToProtoResult;

public class SuperstreamSerializer<T> implements Serializer<T> {
    private Serializer<T> originalSerializer;
    private Superstream superstreamConnection;
    private volatile String compressionType = "zstd";
    private Producer<?, ?> producer;

    public SuperstreamSerializer() {
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        try {
            Object originalSerializerObj = configs.get(Consts.originalSerializer);
            if (originalSerializerObj == null) {
                throw new Exception("original serializer is required");
            }
            Class<?> originalSerializerClass;

            if (originalSerializerObj instanceof String) {
                originalSerializerClass = Class.forName((String) originalSerializerObj);
            } else if (originalSerializerObj instanceof Class) {
                originalSerializerClass = (Class<?>) originalSerializerObj;
            } else {
                throw new Exception("Invalid type for original serializer");
            }
            @SuppressWarnings("unchecked")
            Serializer<T> originalSerializerT = (Serializer<T>) originalSerializerClass.getDeclaredConstructor()
                    .newInstance();
            this.originalSerializer = originalSerializerT;
            this.originalSerializer.configure(configs, isKey);
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
        java.util.function.Function<Properties, Producer<?, ?>> producerCreator =
                (java.util.function.Function<Properties, Producer<?, ?>>) configs.get("superstream.producer.creator");

        if (producerCreator != null) {
            Properties props = new Properties();
            props.putAll(configs);
            this.producer = producerCreator.apply(props);
        }

        if (this.superstreamConnection != null) {
            this.superstreamConnection.setCompressionUpdateCallback(this::onCompressionUpdate);
        }
    }

    private void onCompressionUpdate(boolean enabled, String type) {
        this.compressionType = enabled ? type : "none";
    }

    @Override
    public byte[] serialize(String topic, T data) {
        byte[] serializedData = originalSerializer.serialize(topic, data);
        return serializedData;
    }

    @Override
    public byte[] serialize(String topic, Headers headers, T data) {
        if (originalSerializer == null) {
            return null;
        }
        byte[] serializedData = this.originalSerializer.serialize(topic, headers, data);
        byte[] serializedResult;
        if (serializedData == null) {
            return null;
        }
        if (superstreamConnection != null && superstreamConnection.superstreamReady) {
            if (superstreamConnection.reductionEnabled) {
                if (superstreamConnection.descriptor != null) {
                    try {
                        JsonToProtoResult jsonToProtoResult = superstreamConnection.jsonToProto(serializedData);
                        if (jsonToProtoResult.isSuccess()) {
                            byte[] superstreamSerialized = jsonToProtoResult.getMessageBytes();
                            superstreamConnection.clientCounters.incrementTotalBytesBeforeReduction(serializedData.length);

                            // Применяем сжатие к сериализованным данным
                            byte[] compressedData = compressData(superstreamSerialized);

                            superstreamConnection.clientCounters.incrementTotalBytesAfterReduction(compressedData.length);
                            superstreamConnection.clientCounters.incrementTotalMessagesSuccessfullyProduce();
                            serializedResult = compressedData;
                            headers.add(new RecordHeader("superstream_schema",
                                    superstreamConnection.ProducerSchemaID.getBytes(StandardCharsets.UTF_8)));
                            headers.add(new RecordHeader("superstream.compression.type",
                                    compressionType.getBytes(StandardCharsets.UTF_8)));
                        } else {
                            serializedResult = serializedData;
                            superstreamConnection.clientCounters.incrementTotalBytesAfterReduction(serializedData.length);
                        }
                    } catch (Exception e) {
                        serializedResult = serializedData;
                        superstreamConnection.handleError(String.format("error serializing data: %s", e.getMessage()));
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

    private byte[] compressData(byte[] data) {
        if ("none".equals(compressionType)) {
            return data;
        }
        try {
            switch (compressionType) {
                case "zstd":
                    return compressZstd(data);
                default:
                    return data;
            }
        } catch (Exception e) {
            superstreamConnection.handleError(String.format("Error compressing data: %s", e.getMessage()));
            return data;
        }
    }

    private byte[] compressZstd(byte[] data) {
        return Zstd.compress(data);
    }


    @Override
    public void close() {
        if (this.originalSerializer != null) {
            originalSerializer.close();
        }
        if (superstreamConnection != null) {
            superstreamConnection.close();
        }
    }
}
