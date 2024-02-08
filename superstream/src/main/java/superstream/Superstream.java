package superstream;

import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.DynamicMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FileDescriptor;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;

public class Superstream {
    private String token;
    private String superstreamHost =  "broker.superstream.dev";
    private int learningFactor = 0;

    public Superstream(){
    }

    public Superstream setToken(String token){
        this.token = token;
        return this;
    }

    public String getToken(){
        return this.token;
    }

    public Superstream setSuperstreamHost(String superstreamHost){
        this.superstreamHost = superstreamHost;
        return this;
    }

    public String getSuperstreamHost(){
        return this.superstreamHost;
    }

    public Superstream setLearningFactor(int learningFactor){
        this.learningFactor = learningFactor;
        return this;
    }

    public int getLearningFactor(){
        return this.learningFactor;
    }

    public boolean isTokenSet() {
        return this.token != null;
    }

    public void init(Properties props, byte[] descriptorAsBytes) { //TODO: remove descriptorAsBytes
        try {
            if (token == null) {
                throw new IllegalArgumentException("Token is required");
            }
            if (props.containsKey(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)) { //consumer
                String deserializerClassName = props.getProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG);
                Class<?> originalDeserializerClass = Class.forName(deserializerClassName);
                props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,  SuperstreamDeserializer.class.getName());
                props.put("original.deserializer", originalDeserializerClass.getName());
            } 
            if (props.containsKey(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)) { // producer
                String serializerClassName = props.getProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG);
                Class<?> serializerClass = Class.forName(serializerClassName);
                props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, SuperstreamSerializer.class.getName());
                props.put("original.serializer", serializerClass.getName());
            }
            props.put("superstream.descriptor", descriptorAsBytes); //TODO: remove
            props.put("superstream.host", superstreamHost);
            props.put("superstream.token", token);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static byte[] jsonToProto(byte[] msgBytes, byte[] descriptorAsBytes) throws IOException {
        DescriptorProto descriptorProto = DescriptorProto.parseFrom(descriptorAsBytes);
        FileDescriptorProto fileDescriptorProto = FileDescriptorProto.newBuilder().addMessageType(descriptorProto).build();
        
        try {
            FileDescriptor[] dependencies = {};
            FileDescriptor fileDescriptor = FileDescriptor.buildFrom(fileDescriptorProto, dependencies);
            Descriptors.Descriptor descriptor;
            if (fileDescriptor.getMessageTypes().isEmpty()) {
                throw new IllegalArgumentException("No message types found in the provided descriptor.");
            } else {
                descriptor = fileDescriptor.getMessageTypes().get(0);
            }
            DynamicMessage.Builder builder = DynamicMessage.newBuilder(descriptor);
            String jsonString = new String(msgBytes);
            JsonFormat.parser().merge(jsonString, builder);
            DynamicMessage message = builder.build();
            return message.toByteArray();
        } catch (Exception e) {
            if (e.getMessage().contains("Cannot find field")) {
                return msgBytes;
            }
        }
        return msgBytes;
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
        return null;
    }
}
