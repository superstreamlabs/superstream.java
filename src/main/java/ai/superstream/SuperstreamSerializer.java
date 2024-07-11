package ai.superstream;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

import com.github.luben.zstd.Zstd;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.Serializer;

import ai.superstream.Superstream.JsonToProtoResult;

public class SuperstreamSerializer<T> implements Serializer<T> {
    private Serializer<T> originalSerializer;
    private Superstream superstreamConnection;
    private volatile String compressionType = "none";
    private boolean producerCompressionEnabled = false;

    public SuperstreamSerializer() {
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        try {
            Object originalSerializerObj = configs.get(Consts.originalSerializer);
            if (originalSerializerObj == null) {
                throw new Exception("Original serializer is required");
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
            String configuredCompressionType = (String) configs.get(ProducerConfig.COMPRESSION_TYPE_CONFIG);
            this.producerCompressionEnabled = configuredCompressionType != null && !configuredCompressionType.equals("none");

            if (this.superstreamConnection != null) {
                this.superstreamConnection.setCompressionUpdateCallback(this::onCompressionUpdate);
                this.compressionType = this.superstreamConnection.compressionEnabled ? "zstd" : "none";
            }
            this.compressionType = this.producerCompressionEnabled ? configuredCompressionType : "none";
        } catch (Exception e) {
            String errMsg = String.format("Superstream: Error initializing serializer: %s", e.getMessage());
            if (superstreamConnection != null) {
                superstreamConnection.handleError(errMsg);
            }
            System.out.println(errMsg);
        }
    }

    private void onCompressionUpdate(boolean enabled, String type) {
        if (!this.producerCompressionEnabled) {
            this.compressionType = enabled ? type : "none";
        }
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
        byte[] serializedResult = serializedData;

        if (serializedData == null) {
            return null;
        }

        if (superstreamConnection != null && superstreamConnection.superstreamReady) {
            superstreamConnection.clientCounters.incrementTotalBytesBeforeReduction(serializedData.length);

            if (superstreamConnection.reductionEnabled && superstreamConnection.descriptor != null) {
                try {
                    JsonToProtoResult jsonToProtoResult = superstreamConnection.jsonToProto(serializedData);
                    if (jsonToProtoResult.isSuccess()) {
                        serializedResult = jsonToProtoResult.getMessageBytes();
                        superstreamConnection.clientCounters.incrementTotalMessagesSuccessfullyProduce();
                        Header header = new RecordHeader("superstream_schema",
                                superstreamConnection.ProducerSchemaID.getBytes(StandardCharsets.UTF_8));
                        headers.add(header);
                    }
                } catch (Exception e) {
                    superstreamConnection.handleError(String.format("error serializing data: %s", e.getMessage()));
                    superstreamConnection.clientCounters.incrementTotalMessagesFailedProduce();
                }
            } else if (superstreamConnection.reductionEnabled) {
                if (superstreamConnection.learningFactorCounter <= superstreamConnection.learningFactor) {
                    superstreamConnection.sendLearningMessage(serializedData);
                    superstreamConnection.learningFactorCounter++;
                } else if (!superstreamConnection.learningRequestSent) {
                    superstreamConnection.sendRegisterSchemaReq();
                }
            }


            if (superstreamConnection.compressionEnabled && !producerCompressionEnabled) {
                superstreamConnection.clientCounters.incrementTotalBytesBeforeCompression(serializedResult.length);
                compressionType = "zstd";
                serializedResult = compressData(serializedResult);
                headers.add(new RecordHeader("compression", compressionType.getBytes(StandardCharsets.UTF_8)));
                superstreamConnection.clientCounters.incrementTotalBytesAfterCompression(serializedResult.length);
            }

            superstreamConnection.clientCounters.incrementTotalBytesAfterReduction(serializedResult.length);
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
                    return Zstd.compress(data);
                default:
                    superstreamConnection.handleError("Unsupported compression type: " + compressionType);
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
