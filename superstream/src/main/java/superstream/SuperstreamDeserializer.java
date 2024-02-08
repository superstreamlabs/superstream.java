package superstream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.kafka.common.serialization.Deserializer;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;

public class SuperstreamDeserializer<T> implements Deserializer<T>{
    private Deserializer<T> innerDeserializer;
    private byte[] descriptorAsBytes; // TODO: remove
    

    public SuperstreamDeserializer() {
    }

    @SuppressWarnings("unchecked")
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        String innerDeserializerClassName = (String) configs.get("original.deserializer");
        byte[] descriptorBytes = (byte[]) configs.get("superstream.descriptor");
        descriptorAsBytes = descriptorBytes;
        try {
            Class<?> innerDeserializerClass = Class.forName(innerDeserializerClassName);
            innerDeserializer = (Deserializer<T>) innerDeserializerClass.getDeclaredConstructor().newInstance();
            innerDeserializer.configure(configs, isKey);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public T deserialize(String topic, byte[] data) {
        byte[] dataToDesrialize;
        try {
            byte[] memphisSerialized = protoToJson(data, descriptorAsBytes); // TODO: remove descriptorAsBytes
            dataToDesrialize = memphisSerialized;
        } catch (Exception e) {
            dataToDesrialize = data;
        }
        T deserializedData = innerDeserializer.deserialize(topic, dataToDesrialize);


        return deserializedData;
    }

    @Override
    public void close() {
        innerDeserializer.close();
        // TODO: close memphis resources if needed
    }

    public static byte[] protoToJson(byte[] msgBytes, byte[] descriptorAsBytes) throws IOException {
    DescriptorProto descriptorProto = DescriptorProto.parseFrom(descriptorAsBytes);
    FileDescriptorProto fileDescriptorProto = FileDescriptorProto.newBuilder()
        .addMessageType(descriptorProto)
        .build();
        try {
            FileDescriptor[] dependencies = {};
            FileDescriptor fileDescriptor = FileDescriptor.buildFrom(fileDescriptorProto, dependencies);
            Descriptors.Descriptor descriptor;
            if (fileDescriptor.getMessageTypes().isEmpty()) {
                throw new IllegalArgumentException("No message types found in the provided descriptor.");
            } else {
                descriptor = fileDescriptor.getMessageTypes().get(0);
            }
            DynamicMessage message = DynamicMessage.parseFrom(descriptor, msgBytes);
            String jsonString = JsonFormat.printer().omittingInsignificantWhitespace().print(message);
            return jsonString.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            if (e.getMessage().contains("the input ended unexpectedly")) {
                return msgBytes;
            }
        }
        return msgBytes;
    }
}
