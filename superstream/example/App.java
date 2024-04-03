package example;

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
        try{
            Properties properties = new Properties();

            // Producer Configs
            properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            properties.put("original.serializer", StringSerializer.class.getName());
            properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, SuperstreamSerializer.class.getName());
            
            // Consumer Configs
            properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, SuperstreamDeserializer.class.getName());
            properties.put("original.deserializer", StringDeserializer.class.getName());
            properties.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group23");
            properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

            // Common Configs
            properties.put("superstream.token", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhY2NvdW50X2luZm8iOnsiYWNjb3VudF9pZCI6IjIyMzY3MTk5MSIsImVtYWlsIjoic2hheUBtZW1waGlzLmRldiIsImp3dCI6ImV5SjBlWEFpT2lKS1YxUWlMQ0poYkdjaU9pSmxaREkxTlRFNUxXNXJaWGtpZlEuZXlKcWRHa2lPaUpRTkRKQ1NWUXlORk15VkZWT1RWQlhVbEZTVjBOSVV6ZEhOMVUwU0RVMVIxYzJUMFZJV0RkS1drbEpSMWxEU1VwWFNEWlJJaXdpYVdGMElqb3hOekV3TWpRNU16azRMQ0pwYzNNaU9pSkJRVmhRUkZoSlJ6TXpURXMyTlZGTFZVSkdXVk5GUVRjeldreExSRmhHUkVWRlJ6WktVVU5XUkZsWFYwTTNTMXBRUjFaSFV6STJOU0lzSW01aGJXVWlPaUl5TWpNMk56RTVPVEVpTENKemRXSWlPaUpWUTFKRlVGaFFTMEZhUlUwM1ZsQk9XbFJMTjBSVU5rOU5XVXhLU0RSRFdGbzFTMGxZVTBzMlUwSTFOMU5HUjA0eVRGWlJURTlHV2lJc0ltNWhkSE1pT25zaWNIVmlJanA3ZlN3aWMzVmlJanA3ZlN3aWMzVmljeUk2TFRFc0ltUmhkR0VpT2kweExDSndZWGxzYjJGa0lqb3RNU3dpZEhsd1pTSTZJblZ6WlhJaUxDSjJaWEp6YVc5dUlqb3lmWDAuVWhJWmdfQldvVlRILTB4VWd2WjhCbWJEUjlQTlBORUFseVEyQXM2Wl9DNG9hVjdIdmpiMWpQNXI1alBQQ05OazBhVDlyR1RhQmNNaXo1cHpKSm5yQnciLCJua2V5IjoiU1VBT09UUUgyNE5aM1FRMkdDUjU2R1hIUFVPT1lOSUFYWTdNTk9LQTY0SktaNlZRUE1IUE81NEtLQSIsIm9yZ2FuaXphdGlvbl9uYW1lIjoic2hheSBvcmdhbml6YXRpb24ifX0.not5XTjNX2Xh_Kv84hTMOwmVQ9xSSz5AW3xVOLsn1uM");
            properties.put("superstream.learning.factor", 0);
            properties.put("superstream.host", "localhost:4223");
            properties.put("security.protocol", "SASL_SSL");
            properties.put("sasl.mechanism", "PLAIN");
            properties.put("sasl.jaas.config", 
            "org.apache.kafka.common.security.plain.PlainLoginModule required username='****' password='****';");
            properties.put("client.dns.lookup", "use_all_dns_ips");
            properties.put("bootstrap.servers", "****");
            
            // Create a producer
            KafkaProducer<String, String> producer = new KafkaProducer<>(properties);

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("id", 23);
            jsonMap.put("age", 28);
            jsonMap.put("first", "John");
            jsonMap.put("last", "Bratslavsky");
            jsonMap.put("hello", "Bratslavsky");
            jsonMap.put("world", "Bratslavsky");
            String jsonString = mapper.writeValueAsString(jsonMap);
            // Produce some messages
            for (int i = 0; i < 50000; i++) {
                producer.send(new ProducerRecord<>("sample_topic", Integer.toString(i), jsonString));
            }
            producer.close();
            
            

            // Create a consumer
            KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
            

            // Subscribe to topic
            consumer.subscribe(Collections.singletonList("javajava4"));
            Integer counter = 0;
            // Poll for new data
            try {
                while (true) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    for (ConsumerRecord<String, String> record : records) {
                        String val = record.value();
                        System.out.println(val);
                        counter++;
                        System.out.println(counter);
                    }
                }
            } finally {
                System.out.println(counter);
                consumer.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
