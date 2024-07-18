package ai.superstream;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SuperstreamCounters {
    @JsonProperty("total_bytes_before_reduction")
    public AtomicLong TotalBytesBeforeReduction = new AtomicLong(0);
    @JsonProperty("total_bytes_after_reduction")
    public AtomicLong TotalBytesAfterReduction = new AtomicLong(0);
    @JsonProperty("total_messages_successfully_produce")
    public AtomicInteger TotalMessagesSuccessfullyProduce = new AtomicInteger(0);
    @JsonProperty("total_messages_successfully_consume")
    public AtomicInteger TotalMessagesSuccessfullyConsumed = new AtomicInteger(0);
    @JsonProperty("total_messages_failed_produce")
    public AtomicInteger TotalMessagesFailedProduce = new AtomicInteger(0);
    @JsonProperty("total_messages_failed_consume")
    public AtomicInteger TotalMessagesFailedConsume = new AtomicInteger(0);
    @JsonProperty("total_serialization_reduced")
    public AtomicLong TotalSSMPayloadReduced = new AtomicLong(0);

    public SuperstreamCounters() {
    }

    public void reset() {
        TotalBytesBeforeReduction = new AtomicLong(0);
        TotalBytesAfterReduction = new AtomicLong(0);
        TotalSSMPayloadReduced = new AtomicLong(0);
        TotalMessagesSuccessfullyProduce = new AtomicInteger(0);
        TotalMessagesSuccessfullyConsumed = new AtomicInteger(0);
        TotalMessagesFailedProduce = new AtomicInteger(0);
        TotalMessagesFailedConsume = new AtomicInteger(0);
    }

    public void incrementTotalBytesBeforeReduction(long bytes) {
        TotalBytesBeforeReduction.addAndGet(bytes);
    }

    public void incrementTotalBytesAfterReduction(long bytes) {
        TotalBytesAfterReduction.addAndGet(bytes);
    }

    public void incrementTotalSSMPayloadReduced(long bytes) {
        TotalSSMPayloadReduced.addAndGet(bytes);
    }

    public void incrementTotalMessagesSuccessfullyProduce() {
        TotalMessagesSuccessfullyProduce.incrementAndGet();
    }

    public void sumTotalBeforeReductionTotalSSMPayloadReduced() {
        long valueToAdd = TotalSSMPayloadReduced.getAndSet(0);
        TotalBytesBeforeReduction.addAndGet(valueToAdd);
    }

    public void incrementTotalMessagesSuccessfullyConsumed() {
        TotalMessagesSuccessfullyConsumed.incrementAndGet();
    }

    public void incrementTotalMessagesFailedProduce() {
        TotalMessagesFailedProduce.incrementAndGet();
    }

    public void incrementTotalMessagesFailedConsume() {
        TotalMessagesFailedConsume.incrementAndGet();
    }

    public long getTotalBytesBeforeReduction() {
        return TotalBytesBeforeReduction.get();
    }

    public long getTotalBytesAfterReduction() {
        return TotalBytesAfterReduction.get();
    }

    public int getTotalMessagesSuccessfullyProduce() {
        return TotalMessagesSuccessfullyProduce.get();
    }

    public int getTotalMessagesSuccessfullyConsumed() {
        return TotalMessagesSuccessfullyConsumed.get();
    }

    public int getTotalMessagesFailedProduce() {
        return TotalMessagesFailedProduce.get();
    }

    public int getTotalMessagesFailedConsume() {
        return TotalMessagesFailedConsume.get();
    }

    public void setTotalBytesBeforeReduction(long totalBytesBeforeReduction) {
        TotalBytesBeforeReduction = new AtomicLong(totalBytesBeforeReduction);
    }

    public void setTotalBytesAfterReduction(long totalBytesAfterReduction) {
        TotalBytesAfterReduction = new AtomicLong(totalBytesAfterReduction);
    }

    public void setTotalMessagesSuccessfullyProduce(int totalMessagesSuccessfullyProduce) {
        TotalMessagesSuccessfullyProduce.addAndGet(totalMessagesSuccessfullyProduce);
    }

    public void setTotalMessagesSuccessfullyConsumed(int totalMessagesSuccessfullyConsumed) {
        TotalMessagesSuccessfullyConsumed.addAndGet(totalMessagesSuccessfullyConsumed);
    }

    public void setTotalMessagesFailedProduce(int totalMessagesFailedProduce) {
        TotalMessagesFailedProduce.addAndGet(totalMessagesFailedProduce);
    }

    public void setTotalMessagesFailedConsume(int totalMessagesFailedConsume) {
        TotalMessagesFailedConsume.addAndGet(totalMessagesFailedConsume);
    }
}
