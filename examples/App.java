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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import ai.superstream.Superstream;

public class App
{
    public void main( String[] args ) {
        try{
            Properties properties = new Properties();

            // Producer Configs
            properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            
            // Consumer Configs
            properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group23");
            properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

            // Common Configs
            properties.put("security.protocol", "SASL_SSL");
            properties.put("sasl.mechanism", "PLAIN");
            properties.put("sasl.jaas.config", 
            "org.apache.kafka.common.security.plain.PlainLoginModule required username='****' password='****';");
            properties.put("client.dns.lookup", "use_all_dns_ips");
            properties.put("bootstrap.servers", "****");
            
            properties = Superstream.initSuperstreamProps(properties);
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
