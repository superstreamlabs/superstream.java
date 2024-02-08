package superstream;

import java.io.IOException;
import java.util.Map;

import org.apache.kafka.common.serialization.Serializer;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;

public class SuperstreamSerializer<T> implements Serializer<T>{
    private Serializer<T> innerSerializer;
    private SuperstreamConnection superstreamConnection;

    public SuperstreamSerializer() {
    }

    @SuppressWarnings("unchecked")
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        String innerSerializerClassName = (String) configs.get("original.serializer");
        byte[] descriptorBytes = (byte[]) configs.get("superstream.descriptor");
        String host = (String) configs.get("superstream.host");
        String token = (String) configs.get("superstream.token");
        Class<?> innerSerializerClass;
        try {
            innerSerializerClass = Class.forName(innerSerializerClassName);
            innerSerializer = (Serializer<T>) innerSerializerClass.getDeclaredConstructor().newInstance();
            innerSerializer.configure(configs, isKey);
            superstreamConnection = new SuperstreamConnection(token, host, descriptorBytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public byte[] serialize(String topic, T data) {
        byte[] serializedData = innerSerializer.serialize(topic, data);
        byte[] serializedResult;
        if (superstreamConnection != null && superstreamConnection.messageBuilder != null) {
            try {
                byte[] memphisSerialized = superstreamConnection.jsonToProto(serializedData);
                serializedResult = memphisSerialized;
            } catch (Exception e) {
                serializedResult = serializedData;
            }
        } else {
            serializedResult = serializedData;
        }
        return serializedResult;
    }

    @Override
    public void close() {
        innerSerializer.close();
        superstreamConnection.close();
    }
}
