package superstream;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
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



import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Descriptors.Descriptor;
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
            // Map<String,String> testMap = new HashMap<>();
            // testMap.put("test", "test");
            // String stringTestMAp = mapper.writeValueAsString(testMap);
            // byte[] jsonBytes = jsonString.getBytes(StandardCharsets.UTF_8);
            // byte[] msg = Interceptor.jsonToProto(jsonBytes, descriptorBytes);
            Properties properties = new Properties();
            properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            // properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
            properties.put("original.serializer", StringSerializer.class.getName());
            properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, SuperstreamSerializer.class.getName());
            properties.put("superstream.token", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhY2NvdW50X2luZm8iOnsiYWNjb3VudF9pZCI6IjIyMzY3MTk5MCIsImVtYWlsIjoibWFpbEBleGFtcGxlLmNvbSIsImp3dCI6ImV5SjBlWEFpT2lKS1YxUWlMQ0poYkdjaU9pSmxaREkxTlRFNUxXNXJaWGtpZlEuZXlKcWRHa2lPaUpPVlVSVVNGZEdSVXBEVkZvelJFaEpTMHhHUmtoV1JFcExSRlJQVTFNMU4wOWFUVTlDTlZoT1MwTkJObG95V1ZSVVNrZEJJaXdpYVdGMElqb3hOekV3TVRjeE56TTNMQ0pwYzNNaU9pSkJRVmhRUkZoSlJ6TXpURXMyTlZGTFZVSkdXVk5GUVRjeldreExSRmhHUkVWRlJ6WktVVU5XUkZsWFYwTTNTMXBRUjFaSFV6STJOU0lzSW01aGJXVWlPaUl5TWpNMk56RTVPVEFpTENKemRXSWlPaUpWUkRKRFR6Wk5TazFGUjFSRlFsWXpWalpRTlU5Vk5VSXlURXhHUmtsV1ExZFBObE5WU0VkWFNsTTJRalJXVFRkVVFrUXpRMWhSVkNJc0ltNWhkSE1pT25zaWNIVmlJanA3ZlN3aWMzVmlJanA3ZlN3aWMzVmljeUk2TFRFc0ltUmhkR0VpT2kweExDSndZWGxzYjJGa0lqb3RNU3dpZEhsd1pTSTZJblZ6WlhJaUxDSjJaWEp6YVc5dUlqb3lmWDAuQTBHaTZIVW4xSmZYREpxUjRmaDdoQlMzYlF3NFhWMDVFSURiVGRycVBtSmo1RUlJclJnalBEYkd1OFhzdnhFaHBOUjUteFp3djNJZ25LdDE3TTlfQUEiLCJua2V5IjoiU1VBRUdLWTc3NTNHS0FQVkE1N0JKN1lPQ0hXNVJZT1pWS1RXN0w0UE9YRUJIWUpCQTY1TTM3RjdEVSIsIm9yZ2FuaXphdGlvbl9uYW1lIjoiZXhhbXBsZSBvcmdhbml6YXRpb24ifX0.uy-t2Jkfi8XhCfbeDSF88GjaEO4dvEfJBI56W1EbtjQ");
            properties.put("superstream.learning.factor", 0);
            properties.put("superstream.host", "localhost:4223");
            // properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            // properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
            // properties.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group23");
            // properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

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
            Superstream superstream = new Superstream();
            superstream.setToken("token").setLearningFactor(0).setSuperstreamHost("");
            // superstream.init(properties, descriptorBytes);
            // Superstream.initSuperstream(properties, descriptorBytes);
            // Create a producer
            KafkaProducer<String, String> producer = new KafkaProducer<>(properties);

            // // Produce some messages
            for (int i = 0; i < 1000; i++) {
                producer.send(new ProducerRecord<>("shay_2", Integer.toString(i), jsonString));
                System.out.println(i);
                // ProducerRecord rec  = new ProducerRecord<>("java_json", Integer.toString(i), jsonBytes);
                // rec.
                // producer.send(new ProducerRecord<>("java_json", Integer.toString(i), jsonBytes));
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
            // KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(properties);
            

            // // Subscribe to topic
            // consumer.subscribe(Collections.singletonList("java_json"));

            // KafkaConsumer<String, byte[]> consumer2 = new KafkaConsumer<>(properties);
            // consumer2.subscribe(Collections.singletonList("java_json"));

            // Poll for new data
            // try {
            //     while (true) {
            //         ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(1000));
            //         for (ConsumerRecord<String, byte[]> record : records) {
            //             byte[] val = record.value();
            //             System.out.println(val);
            //             // byte[] json = Interceptor.protoToJson(val, descriptorBytes);
            //             System.out.println("--------------1--------------");
            //             Map<String, Object> map = mapper.readValue(val, Map.class);
            //             System.out.println(map);
            //             System.out.printf("Received record (key: %s, value: %s, partition: %d, offset: %d)\n",
            //                     record.key(), record.value(), record.partition(), record.offset());
            //         }
            //         ConsumerRecords<String, byte[]> records2 = consumer2.poll(Duration.ofMillis(1000));
            //         for (ConsumerRecord<String, byte[]> record : records2) {
            //             byte[] val = record.value();
            //             System.out.println(val);
            //             // byte[] json = Interceptor.protoToJson(val, descriptorBytes);
            //             System.out.println("--------------2--------------");
            //             Map<String, Object> map = mapper.readValue(val, Map.class);
            //             System.out.println(map);
            //             System.out.printf("Received record (key: %s, value: %s, partition: %d, offset: %d)\n",
            //                     record.key(), record.value(), record.partition(), record.offset());
            //         }
            //     }
            // } finally {
            //     consumer.close();
            //     consumer2.close();
            // }
            producer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
