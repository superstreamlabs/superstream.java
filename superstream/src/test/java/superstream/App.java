package superstream;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;



import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import java.io.File;


import superstream.Message.SomeMessage;

public class App 
{
    public static void main( String[] args ) {
        Descriptor descriptor = SomeMessage.getDescriptor();
        DescriptorProto descriptorProto = descriptor.toProto();

        byte[] descriptorBytes = descriptorProto.toByteArray();
        String descriptorAsString = descriptor.toString();

        // System.out.println(descriptorAsString);
        // Map<String, Object> jsonMap = new HashMap<>();
        // jsonMap.put("id", 23);
        // jsonMap.put("age", 28);
        // jsonMap.put("first", "Shay");
        // jsonMap.put("last", "Bratslavsky");
        // jsonMap.put("hello", "Bratslavsky");
        // jsonMap.put("world", "Bratslavsky");
        // System.out.println(jsonMap);
        ObjectMapper mapper = new ObjectMapper();

        try{
            File jsonFile = new File("/Users/shay23bra/memphisdev/superstream.java/superstream/src/main/java/superstream/message.json");
            Map<String, Object> jsonMap = mapper.readValue(jsonFile, Map.class);
            String jsonString = mapper.writeValueAsString(jsonMap);
            byte[] jsonBytes = jsonString.getBytes(StandardCharsets.UTF_8);
            // byte[] msg = Interceptor.jsonToProto(jsonBytes, descriptorBytes);
            Properties properties = new Properties();
            // properties.put(ProducerConfig. , "pkc-7xoy1.eu-central-1.aws.confluent.cloud:9092");
            properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
            // properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            // properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
            // properties.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group23");
            // properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

            // Configure SASL/PLAIN
            properties.put("security.protocol", "SASL_SSL");
            properties.put("sasl.mechanism", "PLAIN");
            properties.put("sasl.jaas.config", 
                "org.apache.kafka.common.security.plain.PlainLoginModule required username='QCGAXYJ6BX5PKQJZ' password='ElBPc7P17zYceuIVYzEpU486OQbPNae444wBLFfs6nhOIBIhN5EcDR4CVqwmuw1u';");



            properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "pkc-7xoy1.eu-central-1.aws.confluent.cloud:9092");
            properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
            properties.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
            properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            Superstream superstream = new Superstream();
            superstream.setToken("token").setLearningFactor(0).setSuperstreamHost("");
            superstream.init(properties, descriptorBytes);
            // Superstream.initSuperstream(properties, descriptorBytes);
            // Create a producer
            KafkaProducer<String, byte[]> producer = new KafkaProducer<>(properties);

            // // Produce some messages
            for (int i = 0; i < 5; i++) {
                producer.send(new ProducerRecord<>("java_json", Integer.toString(i), jsonBytes));
                System.out.println(i);
            }

            // Close the producer
            // producer.close();
            // byte[] json = Interceptor.protoToJson(msg, descriptorBytes);
            // System.out.println("Size of the json is: " + json.length + " bytes");
            // Map<String, Object> map = mapper.readValue(json, Map.class);
            // System.out.println(map);
            
            // System.out.println(json);
            // Properties properties = new Properties();
            
            

            // // Create a consumer
            KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(properties);
            

            // // Subscribe to topic
            consumer.subscribe(Collections.singletonList("java_json"));

            KafkaConsumer<String, byte[]> consumer2 = new KafkaConsumer<>(properties);
            consumer2.subscribe(Collections.singletonList("java_json"));

            // Poll for new data
            try {
                while (true) {
                    ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(1000));
                    for (ConsumerRecord<String, byte[]> record : records) {
                        byte[] val = record.value();
                        System.out.println(val);
                        // byte[] json = Interceptor.protoToJson(val, descriptorBytes);
                        System.out.println("--------------1--------------");
                        Map<String, Object> map = mapper.readValue(val, Map.class);
                        System.out.println(map);
                        System.out.printf("Received record (key: %s, value: %s, partition: %d, offset: %d)\n",
                                record.key(), record.value(), record.partition(), record.offset());
                    }
                    ConsumerRecords<String, byte[]> records2 = consumer2.poll(Duration.ofMillis(1000));
                    for (ConsumerRecord<String, byte[]> record : records2) {
                        byte[] val = record.value();
                        System.out.println(val);
                        // byte[] json = Interceptor.protoToJson(val, descriptorBytes);
                        System.out.println("--------------2--------------");
                        Map<String, Object> map = mapper.readValue(val, Map.class);
                        System.out.println(map);
                        System.out.printf("Received record (key: %s, value: %s, partition: %d, offset: %d)\n",
                                record.key(), record.value(), record.partition(), record.offset());
                    }
                }
            } finally {
                consumer.close();
                consumer2.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
