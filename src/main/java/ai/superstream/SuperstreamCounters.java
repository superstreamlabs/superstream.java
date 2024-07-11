package ai.superstream;

public class SuperstreamCounters {
    public long TotalBytesBeforeReduction = 0;
    public long TotalBytesAfterReduction = 0;
    public int TotalMessagesSuccessfullyProduce = 0;
    public int TotalMessagesSuccessfullyConsumed = 0;
    public int TotalMessagesFailedProduce = 0;
    public int TotalMessagesFailedConsume = 0;

    public SuperstreamCounters() {
    }

    public void reset() {
        TotalBytesBeforeReduction = 0;
        TotalBytesAfterReduction = 0;
        TotalMessagesSuccessfullyProduce = 0;
        TotalMessagesSuccessfullyConsumed = 0;
        TotalMessagesFailedProduce = 0;
        TotalMessagesFailedConsume = 0;
    }

    public void incrementTotalBytesBeforeReduction(long bytes) {
        TotalBytesBeforeReduction += bytes;
    }

    public void incrementTotalBytesAfterReduction(long bytes) {
        TotalBytesAfterReduction += bytes;
    }

    public void incrementTotalMessagesSuccessfullyProduce() {
        TotalMessagesSuccessfullyProduce++;
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
        return TotalBytesBeforeReduction;
    }

    public long getTotalBytesAfterReduction() {
        return TotalBytesAfterReduction;
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
        TotalBytesBeforeReduction = totalBytesBeforeReduction;
    }

    public void setTotalBytesAfterReduction(long totalBytesAfterReduction) {
        TotalBytesAfterReduction = totalBytesAfterReduction;
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
