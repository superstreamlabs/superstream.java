package ai.superstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.concurrent.atomic.AtomicLong;

public class SuperstreamCounters {
    @JsonProperty("total_bytes_before_reduction")
    public AtomicLong TotalBytesBeforeReduction = new AtomicLong(0);
    @JsonProperty("total_bytes_after_reduction")
    public AtomicLong TotalBytesAfterReduction = new AtomicLong(0);
    @JsonProperty("total_messages_successfully_produce")
    public int TotalMessagesSuccessfullyProduce = 0;
    @JsonProperty("total_messages_successfully_consume")
    public int TotalMessagesSuccessfullyConsumed = 0;
    @JsonProperty("total_messages_failed_produce")
    public int TotalMessagesFailedProduce = 0;
    @JsonProperty("total_messages_failed_consume")
    public int TotalMessagesFailedConsume = 0;
    @JsonProperty("total_serialization_reduced")
    public AtomicLong TotalSerializationReduced = new AtomicLong(0);

    public SuperstreamCounters() {
    }

    public void reset() {
        TotalBytesBeforeReduction = new AtomicLong(0);
        TotalBytesAfterReduction = new AtomicLong(0);
        TotalSerializationReduced = new AtomicLong(0);
        TotalMessagesSuccessfullyProduce = 0;
        TotalMessagesSuccessfullyConsumed = 0;
        TotalMessagesFailedProduce = 0;
        TotalMessagesFailedConsume = 0;
    }

    public void incrementTotalBytesBeforeReduction(long bytes) {
        TotalBytesBeforeReduction.addAndGet(bytes);
    }

    public void incrementTotalBytesAfterReduction(long bytes) {
        TotalBytesAfterReduction.addAndGet(bytes);
    }

    public void incrementTotalSerializationReduced(long bytes) {
        TotalSerializationReduced.addAndGet(bytes);
    }

    public void incrementTotalMessagesSuccessfullyProduce() {
        TotalMessagesSuccessfullyProduce++;
    }

    public void sumTotalBeforeReductionTotalSerializationReduced() {
        long valueToAdd = TotalSerializationReduced.getAndSet(0);
        TotalBytesBeforeReduction.addAndGet(valueToAdd);
    }

    public void incrementTotalMessagesSuccessfullyConsumed() {
        TotalMessagesSuccessfullyConsumed++;
    }

    public void incrementTotalMessagesFailedProduce() {
        TotalMessagesFailedProduce++;
    }

    public void incrementTotalMessagesFailedConsume() {
        TotalMessagesFailedConsume++;
    }

    public long getTotalBytesBeforeReduction() {
        return TotalBytesBeforeReduction.get();
    }

    public long getTotalBytesAfterReduction() {
        return TotalBytesAfterReduction.get();
    }

    public int getTotalMessagesSuccessfullyProduce() {
        return TotalMessagesSuccessfullyProduce;
    }

    public int getTotalMessagesSuccessfullyConsumed() {
        return TotalMessagesSuccessfullyConsumed;
    }

    public int getTotalMessagesFailedProduce() {
        return TotalMessagesFailedProduce;
    }

    public int getTotalMessagesFailedConsume() {
        return TotalMessagesFailedConsume;
    }

    public void setTotalBytesBeforeReduction(long totalBytesBeforeReduction) {
        TotalBytesBeforeReduction = new AtomicLong(totalBytesBeforeReduction);
    }

    public void setTotalBytesAfterReduction(long totalBytesAfterReduction) {
        TotalBytesAfterReduction = new AtomicLong(totalBytesAfterReduction);
    }

    public void setTotalMessagesSuccessfullyProduce(int totalMessagesSuccessfullyProduce) {
        TotalMessagesSuccessfullyProduce = totalMessagesSuccessfullyProduce;
    }

    public void setTotalMessagesSuccessfullyConsumed(int totalMessagesSuccessfullyConsumed) {
        TotalMessagesSuccessfullyConsumed = totalMessagesSuccessfullyConsumed;
    }

    public void setTotalMessagesFailedProduce(int totalMessagesFailedProduce) {
        TotalMessagesFailedProduce = totalMessagesFailedProduce;
    }

    public void setTotalMessagesFailedConsume(int totalMessagesFailedConsume) {
        TotalMessagesFailedConsume = totalMessagesFailedConsume;
    }
}
