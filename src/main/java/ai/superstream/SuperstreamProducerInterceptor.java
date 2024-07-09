package ai.superstream;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

public class SuperstreamProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {
    Superstream superstreamConnection;
    public SuperstreamProducerInterceptor() {}

    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        if (this.superstreamConnection != null) {
            if (record != null) {
                this.superstreamConnection.updateTopicPartitions(record.topic(), record.partition());
            }
        }

        String compressionType = null;
        assert record != null;
        for (Header header : record.headers()) {
            if ("superstream.compression.type".equals(header.key())) {
                compressionType = new String(header.value(), StandardCharsets.UTF_8);
                break;
            }
        }
        if (compressionType != null && !"none".equals(compressionType)) {
            record.headers().remove("compression.type");
            record.headers().add("compression.type", compressionType.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
    }
    
    @Override
    public void close() {
    }

    @Override
    public void configure(Map<String, ?> configs) {
        Superstream superstreamConn = (Superstream) configs.get(Consts.superstreamConnectionKey);
        if (superstreamConn != null) {
            this.superstreamConnection = superstreamConn;
        }
    }
}
