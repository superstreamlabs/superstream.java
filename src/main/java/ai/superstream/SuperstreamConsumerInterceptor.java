package ai.superstream;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;


public class SuperstreamConsumerInterceptor<K, V> implements ConsumerInterceptor<K, V> {
    Superstream superstreamConnection;
    public SuperstreamConsumerInterceptor(){}
    @Override
    public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
        records.forEach(record -> {
            this.superstreamConnection.executorService.submit(() -> {
                this.superstreamConnection.updateTopicPartitions(record.topic(), record.partition());
            });
        });
        return records;
    }

    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
    }

    @Override
    public void close() {
    }

    @Override
    public void configure(Map<String, ?> configs) {
    }
}
