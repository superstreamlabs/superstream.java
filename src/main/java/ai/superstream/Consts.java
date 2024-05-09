package ai.superstream;

public class Consts {
    public static final String sdkVersion  = "1.0.2";
    public static final String clientReconnectionUpdateSubject  = "internal.clientReconnectionUpdate";
    public static final String clientTypeUpdateSubject  = "internal.clientTypeUpdate";
    public static final String clientRegisterSubject  = "internal.registerClient";
    public static final String originalSerializer = "original.serializer";
    public static final String originalDeserializer = "original.deserializer";
    public static final String superstreamDefaultToken = "no-auth";
    public static final String superstreamErrorSubject = "internal.clientErrors";
    public static final String superstreamUpdatesSubject  = "internal.updates.%d";
    public static final String superstreamClientsUpdateSubject  = "internal_tasks.clientsUpdate.%s.%d";
    public static final String superstreamLearningSubject  = "internal.schema.learnSchema.%d";
    public static final String superstreamRegisterSchemaSubject  = "internal_tasks.schema.registerSchema.%d";
    public static final String superstreamInternalUsername = "superstream_internal";
    public static final String superstreamGetSchemaSubject = "internal.schema.getSchema.%d";
    public static final Integer superstreamDefaultLearningFactor = 20;
    public static final String superstreamLearningFactorKey = "superstream.learning.factor";
    public static final String superstreamHostKey = "superstream.host";
    public static final String superstreamTokenKey = "superstream.token";
    public static final String superstreamReductionEnabledKey = "superstream.reduction.enabled";
    public static final String superstreamConnectionKey = "superstream.connection";
    public static final String superstreamInnerConsumerKey = "superstream.inner.consumer";
    public static final String superstreamMetadataTopic = "superstream.metadata";
}
