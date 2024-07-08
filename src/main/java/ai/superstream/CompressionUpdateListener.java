package ai.superstream;
import org.apache.kafka.common.record.CompressionType;

public interface CompressionUpdateListener {
    void onCompressionUpdate(boolean enabled, CompressionType compressionType);
}
