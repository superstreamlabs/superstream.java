package superstream;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
// import org.apache.kafka.clients.consumer.ConsumerRecord;
// import org.apache.kafka.clients.consumer.KafkaConsumer;
// import org.apache.kafka.clients.consumer.ConsumerConfig;
// import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.omg.PortableInterceptor.Interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Descriptors.Descriptor;

import superstream.Message.SomeMessage;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import java.io.File;

public class App 
{
    public static void main( String[] args ) {
        // Descriptor descriptor = SomeMessage.getDescriptor(); 
        // DescriptorProto descriptorProto = descriptor.toProto();

        // byte[] descriptorBytes = descriptorProto.toByteArray();
        // String descriptorAsString = descriptor.toString();

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
            Map<String,String> testMap = new HashMap<>();
            testMap.put("test", "test");
            // String stringTestMAp = mapper.writeValueAsString(testMap);
            // byte[] jsonBytes = jsonString.getBytes(StandardCharsets.UTF_8);
            // byte[] msg = Interceptor.jsonToProto(jsonBytes, descriptorBytes);
            Properties properties = new Properties();
            properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            // properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
            properties.put("original.serializer", StringSerializer.class.getName());
            properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, SuperstreamSerializer.class.getName());
            properties.put("superstream.token", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhY2NvdW50X2luZm8iOnsiYWNjb3VudF9pZCI6IjIyMzY3MTk5MSIsImVtYWlsIjoic2hheUBtZW1waGlzLmRldiIsImp3dCI6ImV5SjBlWEFpT2lKS1YxUWlMQ0poYkdjaU9pSmxaREkxTlRFNUxXNXJaWGtpZlEuZXlKcWRHa2lPaUpRTkRKQ1NWUXlORk15VkZWT1RWQlhVbEZTVjBOSVV6ZEhOMVUwU0RVMVIxYzJUMFZJV0RkS1drbEpSMWxEU1VwWFNEWlJJaXdpYVdGMElqb3hOekV3TWpRNU16azRMQ0pwYzNNaU9pSkJRVmhRUkZoSlJ6TXpURXMyTlZGTFZVSkdXVk5GUVRjeldreExSRmhHUkVWRlJ6WktVVU5XUkZsWFYwTTNTMXBRUjFaSFV6STJOU0lzSW01aGJXVWlPaUl5TWpNMk56RTVPVEVpTENKemRXSWlPaUpWUTFKRlVGaFFTMEZhUlUwM1ZsQk9XbFJMTjBSVU5rOU5XVXhLU0RSRFdGbzFTMGxZVTBzMlUwSTFOMU5HUjA0eVRGWlJURTlHV2lJc0ltNWhkSE1pT25zaWNIVmlJanA3ZlN3aWMzVmlJanA3ZlN3aWMzVmljeUk2TFRFc0ltUmhkR0VpT2kweExDSndZWGxzYjJGa0lqb3RNU3dpZEhsd1pTSTZJblZ6WlhJaUxDSjJaWEp6YVc5dUlqb3lmWDAuVWhJWmdfQldvVlRILTB4VWd2WjhCbWJEUjlQTlBORUFseVEyQXM2Wl9DNG9hVjdIdmpiMWpQNXI1alBQQ05OazBhVDlyR1RhQmNNaXo1cHpKSm5yQnciLCJua2V5IjoiU1VBT09UUUgyNE5aM1FRMkdDUjU2R1hIUFVPT1lOSUFYWTdNTk9LQTY0SktaNlZRUE1IUE81NEtLQSIsIm9yZ2FuaXphdGlvbl9uYW1lIjoic2hheSBvcmdhbml6YXRpb24ifX0.not5XTjNX2Xh_Kv84hTMOwmVQ9xSSz5AW3xVOLsn1uM");
            properties.put("superstream.learning.factor", 0);
            properties.put("superstream.host", "localhost:4223");
            properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, SuperstreamDeserializer.class.getName());
            properties.put("original.deserializer", StringDeserializer.class.getName());
            properties.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group23");
            properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

            // Configure SASL/PLAIN
            properties.put("security.protocol", "SASL_SSL");
            properties.put("sasl.mechanism", "PLAIN");
            properties.put("sasl.jaas.config", 
            "org.apache.kafka.common.security.plain.PlainLoginModule required username='OIDPBC4NBJ3JTX5J' password='l+IZatOGabtqKDrOsc/yTPhxRQo4BliVHBOi+lRxSOOSHWGaJinklZI6z6CNHc3B';");
            properties.put("client.dns.lookup", "use_all_dns_ips");



            properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "pkc-7xoy1.eu-central-1.aws.confluent.cloud:9092");
            // properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            // properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
            // properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            // properties.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
            // properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            // superstream.init(properties, descriptorBytes);
            // Superstream.initSuperstream(properties, descriptorBytes);
            // Create a producer
            KafkaProducer<String, String> producer = new KafkaProducer<>(properties);

            // // // Produce some messages
            for (int i = 0; i < 50000; i++) {
                producer.send(new ProducerRecord<>("javajava4", Integer.toString(i), jsonString));
                // producer.send(new ProducerRecord<>("shay_2", Integer.toString(i), "hello worldhello worldhello worldhello worldhello worldhello worldhello worldhello worldhello worldhello worldhello worldhello worldhello worldhello worldhello worldhello world"));
                // System.out.println(i);
                // ProducerRecord rec  = new ProducerRecord<>("java_json", Integer.toString(i), jsonBytes);
                // rec.
                // producer.send(new ProducerRecord<>("java_json", Integer.toString(i), jsonBytes));
            }
            producer.close();

            // Close the producer
            // producer.close();
            // byte[] json = Interceptor.protoToJson(msg, descriptorBytes);
            // System.out.println("Size of the json is: " + json.length + " bytes");
            // Map<String, Object> map = mapper.readValue(json, Map.class);
            // System.out.println(map);
            
            // System.out.println(json);
            // Properties properties = new Properties();
            
            

            // Create a consumer
            KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
            

            // // Subscribe to topic
            consumer.subscribe(Collections.singletonList("javajava4"));

            // KafkaConsumer<String, byte[]> consumer2 = new KafkaConsumer<>(properties);
            // consumer2.subscribe(Collections.singletonList("java_json"));
            Integer counter = 0;
            // Poll for new data
            try {
                while (true) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    for (ConsumerRecord<String, String> record : records) {
                        String val = record.value();
                        if (counter > 5000) {
                            System.out.println(val);
                        }
                        // byte[] json = Interceptor.protoToJson(val, descriptorBytes);
                        // Map<String, Object> jsonMapconsumed = mapper.readValue(val, Map.class);
                        // System.out.println(jsonMapconsumed);
                        // System.out.println("--------------1--------------");
                        // Map<String, Object> map = mapper.readValue(val, Map.class);
                        // System.out.println(map);
                        counter++;
                        // System.out.printf("Received record (key: %s, value: %s, partition: %d, offset: %d)\n",
                        // record.key(), record.value(), record.partition(), record.offset());
                        System.out.println(counter);
                    }
                    // ConsumerRecords<String, byte[]> records2 = consumer2.poll(Duration.ofMillis(1000));
                    // for (ConsumerRecord<String, byte[]> record : records2) {
                    //     byte[] val = record.value();
                    //     System.out.println(val);
                    //     // byte[] json = Interceptor.protoToJson(val, descriptorBytes);
                    //     System.out.println("--------------2--------------");
                    //     Map<String, Object> map = mapper.readValue(val, Map.class);
                    //     System.out.println(map);
                    //     System.out.printf("Received record (key: %s, value: %s, partition: %d, offset: %d)\n",
                    //             record.key(), record.value(), record.partition(), record.offset());
                    // }
                }
            } finally {
                System.out.println(counter);
                consumer.close();
            //     consumer2.close();
            }
            // producer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
