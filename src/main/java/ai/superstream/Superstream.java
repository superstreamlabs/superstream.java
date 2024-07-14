package ai.superstream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.Properties;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.protobuf.DescriptorProtos;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.Subscription;
import io.nats.client.api.ServerInfo;
import io.nats.client.ConnectionListener;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.MessageHandler;

public class Superstream {
    public Connection brokerConnection;
    public JetStream jetstream;
    public String superstreamJwt;
    public String superstreamNkey;
    public byte[] descriptorAsBytes;
    public Descriptors.Descriptor descriptor;
    public String natsConnectionID;
    public String clientHash;
    public String accountName;
    public int learningFactor = 20;
    public int learningFactorCounter = 0;
    public boolean learningRequestSent = false;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public String ProducerSchemaID = "0";
    public String ConsumerSchemaID = "0";
    public Map<String, Descriptors.Descriptor> SchemaIDMap = new HashMap<>();
    public Map<String, Object> configs;
    public SuperstreamCounters clientCounters = new SuperstreamCounters();
    private Subscription updatesSubscription;
    private String host;
    private String token;
    public String type;
    public Boolean reductionEnabled;
    public Map<String, Set<Integer>> topicPartitions = new ConcurrentHashMap<>();
    public ExecutorService executorService = Executors.newFixedThreadPool(3);
    private Integer kafkaConnectionID = 0;
    public Boolean superstreamReady = false;
    private String tags = "";
    public Boolean canStart = false;
    private CompressionUpdateCallback compressionUpdateCallback;
    public Boolean compressionEnabled;

    public Superstream(String token, String host, Integer learningFactor, Map<String, Object> configs,
            Boolean enableReduction, String type, String tags) {
        this.learningFactor = learningFactor;
        this.token = token;
        this.host = host;
        this.configs = configs;
        this.reductionEnabled = enableReduction;
        this.type = type;
        this.tags = tags;
        this.compressionEnabled = getBooleanEnv("SUPERSTREAM_COMPRESSION_ENABLED", false);
    }

    public Superstream(String token, String host, Integer learningFactor, Map<String, Object> configs,
            Boolean enableReduction, String type) {
        this(token, host, learningFactor, configs, enableReduction, type, "");
    }

    public void init() {
        executorService.submit(() -> {
            try {
                    initializeNatsConnection(token, host);
                    if (this.brokerConnection != null) {
                        registerClient(configs);
                        waitForStart();
                        if (!canStart) {
                            throw new Exception("Could not start superstream");
                        }
                        subscribeToUpdates();
                        superstreamReady = true;
                        reportClientsUpdate();
                        sendClientTypeUpdateReq();
                    }
                } catch (Exception e) {
                    handleError(e.getMessage());
                }
        });
    }

    public void close() {
        try {
            if (brokerConnection != null) {
                brokerConnection.close();
            }
            executorService.shutdown();
        } catch (Exception e) {
        }
    }

    public void setCompressionUpdateCallback(CompressionUpdateCallback callback) {
        this.compressionUpdateCallback = callback;
    }

    private Boolean getBooleanEnv(String key, Boolean defaultValue) {
        String value = System.getenv(key);
        return (value != null) ? Boolean.parseBoolean(value) : defaultValue;
    }

    public interface CompressionUpdateCallback {
        void onCompressionUpdate(boolean enabled, String type);
    }

    public void incrementTotalBytesAfterReduction(int bytes) {
        this.clientCounters.incrementTotalBytesAfterReduction(bytes);
    }

    private void initializeNatsConnection(String token, String host) {
        try {
            Options options = new Options.Builder()
                    .server(host)
                    .userInfo(Consts.superstreamInternalUsername, token)
                    .maxReconnects(-1)
                    .connectionTimeout(Duration.ofSeconds(10))
                    .reconnectWait(Duration.ofSeconds(1))
                    .connectionListener(new ConnectionListener() {
                        @Override
                        public void connectionEvent(Connection conn, Events type) {
                            if (type == Events.DISCONNECTED) {
                                brokerConnection = null;
                                superstreamReady = false;
                                System.out.println("superstream: Disconnected");
                            } else if (type == Events.RECONNECTED) {
                                try {
                                    brokerConnection = conn;
                                    if (brokerConnection != null) {
                                        natsConnectionID = generateNatsConnectionID();
                                        Map<String, Object> reqData = new HashMap<>();
                                        reqData.put("new_nats_connection_id", natsConnectionID);
                                        reqData.put("client_hash", clientHash);
                                        ObjectMapper mapper = new ObjectMapper();
                                        byte[] reqBytes = mapper.writeValueAsBytes(reqData);
                                        brokerConnection.publish(Consts.clientReconnectionUpdateSubject, reqBytes);
                                        subscribeToUpdates();
                                        superstreamReady = true;
                                        reportClientsUpdate();
                                    }
                                } catch (Exception e) {
                                    System.out.println(
                                            "superstream: Failed to reconnect: " + e.getMessage());
                                }
                                System.out.println("superstream: Reconnected to superstream");
                            }
                        }
                    })
                    .build();

            Connection nc = Nats.connect(options);
            if (nc == null) {
                throw new Exception(String.format("Failed to connect to host: %s", host));
            }
            JetStream js = nc.jetStream();
            if (js == null) {
                throw new Exception(String.format("Failed to connect to host: %s", host));
            }
            brokerConnection = nc;
            jetstream = js;
            natsConnectionID = generateNatsConnectionID();
        } catch (Exception e) {
            System.out.println(String.format("superstream: %s", e.getMessage()));
        }
    }

    private String generateNatsConnectionID() {
        ServerInfo serverInfo = brokerConnection.getServerInfo();
        String connectedServerName = serverInfo.getServerName();
        int serverClientID = serverInfo.getClientId();
        return connectedServerName + ":" + serverClientID;
    }

    public void registerClient(Map<String, ?> configs) {
        try {
            String kafkaConnID = consumeConnectionID();
            if (kafkaConnID != null) {
                try {
                    kafkaConnectionID = Integer.parseInt(kafkaConnID);
                } catch (Exception e) {
                    kafkaConnectionID = 0;
                }
            }
            Map<String, Object> reqData = new HashMap<>();
            reqData.put("nats_connection_id", natsConnectionID);
            reqData.put("language", "java");
            reqData.put("learning_factor", learningFactor);
            reqData.put("version", Consts.sdkVersion);
            reqData.put("config", normalizeClientConfig(configs));
            reqData.put("reduction_enabled", reductionEnabled);
            reqData.put("connection_id", kafkaConnectionID);
            reqData.put("tags", tags);
            ObjectMapper mapper = new ObjectMapper();
            byte[] reqBytes = mapper.writeValueAsBytes(reqData);
            Message reply = brokerConnection.request(Consts.clientRegisterSubject, reqBytes, Duration.ofMinutes(5));
            if (reply != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> replyData = mapper.readValue(reply.getData(), Map.class);
                Object clientHashObject = replyData.get("client_hash");
                if (clientHashObject != null) {
                    clientHash = clientHashObject.toString();
                } else {
                    System.out.println("superstream: client_hash is not a valid string: " + clientHashObject);
                }
                Object accountNameObject = replyData.get("account_name");
                if (accountNameObject != null) {
                    accountName = accountNameObject.toString();
                } else {
                    System.out.println("superstream: account_name is not a valid string: " + accountNameObject);
                }
                Object learningFactorObject = replyData.get("learning_factor");
                if (learningFactorObject instanceof Integer) {
                    learningFactor = (Integer) learningFactorObject;
                } else if (learningFactorObject instanceof String) {
                    try {
                        learningFactor = Integer.parseInt((String) learningFactorObject);
                    } catch (NumberFormatException e) {
                        System.out.println(
                                "superstream: learning_factor is not a valid integer: " + learningFactorObject);
                    }
                } else {
                    System.out.println("superstream: learning_factor is not a valid integer: " + learningFactorObject);
                }
            } else {
                String errMsg = "superstream: registering client: No reply received within the timeout period.";
                System.out.println(errMsg);
                handleError(errMsg);
            }
        } catch (Exception e) {
            System.out.println(String.format("superstream: %s", e.getMessage()));
        }
    }

    private void waitForStart() {
        CountDownLatch latch = new CountDownLatch(1);
        Dispatcher dispatcher = brokerConnection.createDispatcher((msg) -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> messageData = mapper.readValue(msg.getData(), Map.class);
                if (messageData.containsKey("start")) {
                    boolean start = (Boolean) messageData.get("start");
                    if (start) {
                        canStart = true;
                        latch.countDown(); // continue and stop the wait
                    } else {
                        String err = (String) messageData.get("error");
                        System.out.println("superstream: Could not start superstream: " + err);
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        dispatcher.subscribe(String.format(Consts.clientStartSubject, clientHash)); // replace with your specific subject

        try {
            if (!latch.await(10, TimeUnit.MINUTES)) {
                System.out.println("superstream: Could not connect to superstream for 10 minutes.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("superstream: Could not start superstream: " + e.getMessage());
        } finally {
            dispatcher.unsubscribe(String.format(Consts.clientStartSubject, clientHash));
        }
    }

    private String consumeConnectionID() {
        Properties consumerProps = copyAuthConfig();
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        consumerProps.put(Consts.superstreamInnerConsumerKey, "true");
        consumerProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        String connectionId = null;
        KafkaConsumer<String, String> consumer = null;
        try {
            consumer = new KafkaConsumer<>(consumerProps);
            List<PartitionInfo> partitions = consumer.partitionsFor(Consts.superstreamMetadataTopic, Duration.ofMillis(10000));
            if (partitions == null || partitions.isEmpty()) {
                if (consumer != null) {
                    consumer.close();
                }
                return "0";
            }
            TopicPartition topicPartition = new TopicPartition(Consts.superstreamMetadataTopic, 0);
            consumer.assign(Collections.singletonList(topicPartition));
            consumer.seekToEnd(Collections.singletonList(topicPartition));
            long endOffset = consumer.position(topicPartition);
            if (endOffset > 0) {
                consumer.seek(topicPartition, endOffset - 1);
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(10000));
                if (!records.isEmpty()) {
                    connectionId = records.iterator().next().value();
                }
            }
        } catch (Exception e) {
            if (e.getMessage().toLowerCase().contains("timeout")) {
                try {
                    Thread.sleep(10000);
                    if (consumer == null) {
                        consumer = new KafkaConsumer<>(consumerProps);
                    }
                    List<PartitionInfo> partitions = consumer.partitionsFor(Consts.superstreamMetadataTopic, Duration.ofMillis(10000));
                    if (partitions == null || partitions.isEmpty()) {
                        if (consumer != null) {
                            consumer.close();
                        }
                        return "0";
                    }
                    TopicPartition topicPartition = new TopicPartition(Consts.superstreamMetadataTopic, 0);
                    consumer.assign(Collections.singletonList(topicPartition));
                    consumer.seekToEnd(Collections.singletonList(topicPartition));
                    long endOffset = consumer.position(topicPartition);
                    if (endOffset > 0) {
                        consumer.seek(topicPartition, endOffset - 1);
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(10000));
                        if (!records.isEmpty()) {
                            connectionId = records.iterator().next().value();
                        }
                    }
                } catch (Exception e2) {
                    handleError(String.format("consumeConnectionID retry: %s", e2.getMessage()));
                }
            }
            if (connectionId == null || connectionId.equals("0")) {
                handleError(String.format("consumeConnectionID: %s", e.getMessage()));
                if (consumer != null) {
                    consumer.close();
                }
                return "0";
            }
        } finally {
            if (consumer != null) {
                consumer.close();
            }
        }
        return connectionId != null ? connectionId : "0";
    }

    private Properties copyAuthConfig() {
        String[] relevantKeys = {
                // Authentication-related keys
                "security.protocol",
                "ssl.truststore.location",
                "ssl.truststore.password",
                "ssl.keystore.location",
                "ssl.keystore.password",
                "ssl.key.password",
                "ssl.endpoint.identification.algorithm",
                "sasl.mechanism",
                "sasl.jaas.config",
                "sasl.kerberos.service.name",
                // Networking-related keys
                "bootstrap.servers",
                "client.dns.lookup",
                "connections.max.idle.ms",
                "request.timeout.ms",
                "metadata.max.age.ms",
                "reconnect.backoff.ms",
                "reconnect.backoff.max.ms"
        };

        Properties relevantProps = new Properties();
        for (String key : relevantKeys) {
            if (configs.containsKey(key)) {
                if (key == ProducerConfig.BOOTSTRAP_SERVERS_CONFIG) {
                    Object value = configs.get(key);
                    if (value instanceof String[]) {
                        relevantProps.put(key, Arrays.toString((String[]) value));
                    } else if (value instanceof ArrayList) {
                        @SuppressWarnings("unchecked")
                        ArrayList<String> arrayList = (ArrayList<String>) value;
                        relevantProps.put(key, String.join(", ", arrayList));
                    } else {
                        relevantProps.put(key, value);
                    }
                } else {
                    relevantProps.put(key, String.valueOf(configs.get(key)));
                }
            }
        }
        return relevantProps;
    }

    public void sendClientTypeUpdateReq() {
        if (type == "" || type == null) {
            return;
        }
        if (type != "consumer" && type != "producer") {
            return;
        }
        try {
            Map<String, Object> reqData = new HashMap<>();
            reqData.put("client_hash", clientHash);
            reqData.put("type", type);
            ObjectMapper mapper = new ObjectMapper();
            byte[] reqBytes = mapper.writeValueAsBytes(reqData);
            brokerConnection.publish(Consts.clientTypeUpdateSubject, reqBytes);
        } catch (Exception e) {
            handleError(String.format("sendClientTypeUpdateReq: %s", e.getMessage()));
        }
    }

    public void subscribeToUpdates() {
        try {
            String subject = String.format(Consts.superstreamUpdatesSubject, clientHash);
            Dispatcher dispatcher = brokerConnection.createDispatcher(this.updatesHandler());
            updatesSubscription = dispatcher.subscribe(subject, this.updatesHandler());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void reportClientsUpdate() {
        ScheduledExecutorService singleExecutorService = Executors.newSingleThreadScheduledExecutor();
        singleExecutorService.scheduleAtFixedRate(() -> {
            try {
                if (brokerConnection != null && superstreamReady){
                    byte[] byteCounters = objectMapper.writeValueAsBytes(clientCounters);
                    Map<String, Object> topicPartitionConfig = new HashMap<>();
                    if (!topicPartitions.isEmpty()) {
                        Map<String, Integer[]> topicPartitionsToSend = convertMap(topicPartitions);
                        switch (this.type) {
                            case "producer":
                                topicPartitionConfig.put("producer_topics_partitions", topicPartitionsToSend);
                                topicPartitionConfig.put("consumer_group_topics_partitions",
                                        new HashMap<String, Integer[]>());
                                break;
                            case "consumer":
                                topicPartitionConfig.put("producer_topics_partitions", new HashMap<String, Integer[]>());
                                topicPartitionConfig.put("consumer_group_topics_partitions", topicPartitionsToSend);
                                break;
                        }
                    }
                    byte[] byteConfig = objectMapper.writeValueAsBytes(topicPartitionConfig);
                    brokerConnection.publish(String.format(Consts.superstreamClientsUpdateSubject, "counters", clientHash),
                            byteCounters);
                    brokerConnection.publish(String.format(Consts.superstreamClientsUpdateSubject, "config", clientHash),
                            byteConfig);
                }
            } catch (Exception e) {
                handleError("reportClientsUpdate: " + e.getMessage());
            }
        }, 0, 10, TimeUnit.MINUTES);
    }

    public static Map<String, Integer[]> convertMap(Map<String, Set<Integer>> topicPartitions) {
        Map<String, Integer[]> result = new HashMap<>();
        for (Map.Entry<String, Set<Integer>> entry : topicPartitions.entrySet()) {
            Integer[] array = entry.getValue().toArray(new Integer[0]);
            result.put(entry.getKey(), array);
        }
        return result;
    }

    public void sendLearningMessage(byte[] msg) {
        try {
            brokerConnection.publish(String.format(Consts.superstreamLearningSubject, clientHash), msg);
        } catch (Exception e) {
            handleError("sendLearningMessage: " + e.getMessage());
        }
    }

    public void sendRegisterSchemaReq() {
        try {
            brokerConnection.publish(String.format(Consts.superstreamRegisterSchemaSubject, clientHash), new byte[0]);
            learningRequestSent = true;
        } catch (Exception e) {
            handleError("sendLearningMessage: " + e.getMessage());
        }
    }

    public JsonToProtoResult jsonToProto(byte[] msgBytes) throws Exception {
        try {
            String jsonString = new String(msgBytes);
            if (!isJsonObject(jsonString)) {
                jsonString = convertEscapedJsonString(jsonString);
            }
            if (jsonString == null || jsonString.isEmpty()) {
                return new JsonToProtoResult(false, msgBytes);
            }
            if (jsonString != null && jsonString.length() > 2 && jsonString.startsWith("\"{") && jsonString.endsWith("}\"")) {
                jsonString = jsonString.substring(1, jsonString.length() - 1);
            }
            DynamicMessage.Builder newMessageBuilder = DynamicMessage.newBuilder(descriptor);
            JsonFormat.parser().merge(jsonString, newMessageBuilder);
            DynamicMessage message = newMessageBuilder.build();
            return new JsonToProtoResult(true, message.toByteArray());
        } catch (Exception e) {
            return new JsonToProtoResult(false, msgBytes);
        }
    }

    public class JsonToProtoResult {
        private final boolean success;
        private final byte[] messageBytes;

        public JsonToProtoResult(boolean success, byte[] messageBytes) {
            this.success = success;
            this.messageBytes = messageBytes;
        }

        public boolean isSuccess() {
            return success;
        }

        public byte[] getMessageBytes() {
            return messageBytes;
        }
    }

    private boolean isJsonObject(String jsonString) {
        try {
            JsonParser.parseString(jsonString).getAsJsonObject();
            return true;
        } catch (JsonSyntaxException | IllegalStateException e) {
            return false;
        }
    }

    private static String convertEscapedJsonString(String escapedJsonString) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(escapedJsonString);
        return mapper.writeValueAsString(jsonNode).replace("\\\"", "\"").replace("\\\\", "\\");
    }


    public byte[] protoToJson(byte[] msgBytes, Descriptors.Descriptor desc) throws Exception {
        try {
            DynamicMessage message = DynamicMessage.parseFrom(desc, msgBytes);
            String jsonString = JsonFormat.printer().omittingInsignificantWhitespace().print(message);
            return jsonString.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            if (e.getMessage().contains("the input ended unexpectedly")) {
                return msgBytes;
            } else {
                throw e;
            }
        }
    }

    private MessageHandler updatesHandler() {
        return (msg) -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> update = objectMapper.readValue(msg.getData(), Map.class);
                processUpdate(update);
            } catch (IOException e) {
                handleError("updatesHandler at json.Unmarshal: " + e.getMessage());
            }
        };
    }

    private void processUpdate(Map<String, Object> update) {
        String type = (String) update.get("type");
        try {
            String payloadBytesString = (String) update.get("payload");
            byte[] payloadBytes = Base64.getDecoder().decode(payloadBytesString);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(payloadBytes, Map.class);
            switch (type) {
                case "LearnedSchema":
                    String descriptorBytesString = (String) payload.get("desc");
                    String masterMsgName = (String) payload.get("master_msg_name");
                    String fileName = (String) payload.get("file_name");
                    descriptor = compileMsgDescriptor(descriptorBytesString, masterMsgName, fileName);
                    String schemaID = (String) payload.get("schema_id");
                    ProducerSchemaID = schemaID;
                    break;

                case "ToggleReduction":
                    Boolean enableReduction = (Boolean) payload.get("enable_reduction");
                    if (enableReduction) {
                        this.reductionEnabled = true;
                    } else {
                        this.reductionEnabled = false;
                    }
                    break;

                case "CompressionUpdate":
                    Boolean enableCompression = (Boolean) payload.get("enable_compression");
                    String compressionType = (String) payload.get("compression_type");
                    this.compressionEnabled = enableCompression;
                    if (compressionUpdateCallback != null) {
                        compressionUpdateCallback.onCompressionUpdate(enableCompression, compressionType);
                    }
                    break;
            }
        } catch (Exception e) {
            handleError(("processUpdate: " + e.getMessage()));
        }
    }

    public void sendGetSchemaRequest(String schemaID) {
        try {
            Map<String, Object> reqData = new HashMap<>();
            reqData.put("schema_id", schemaID);
            ObjectMapper mapper = new ObjectMapper();
            byte[] reqBytes = mapper.writeValueAsBytes(reqData);
            Message msg = brokerConnection.request(String.format(Consts.superstreamGetSchemaSubject, clientHash),
                    reqBytes, Duration.ofSeconds(5));
            if (msg == null) {
                throw new Exception("Could not get descriptor");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> respMap = objectMapper.readValue(new String(msg.getData(), StandardCharsets.UTF_8),
                    Map.class);
            if (respMap.containsKey("desc") && respMap.get("desc") instanceof String) {
                String descriptorBytesString = (String) respMap.get("desc");
                String masterMsgName = (String) respMap.get("master_msg_name");
                String fileName = (String) respMap.get("file_name");
                Descriptors.Descriptor respDescriptor = compileMsgDescriptor(descriptorBytesString, masterMsgName,
                        fileName);
                if (respDescriptor != null) {
                    SchemaIDMap.put((String) respMap.get("schema_id"), respDescriptor);
                } else {
                    throw new Exception("Error compiling schema.");
                }
            } else {
                throw new Exception("Response map does not contain expected keys.");
            }
        } catch (Exception e) {
            handleError(String.format("sendGetSchemaRequest: %s", e.getMessage()));
        }
    }

    private Descriptors.Descriptor compileMsgDescriptor(String descriptorBytesString, String masterMsgName,
            String fileName) {
        try {
            byte[] descriptorAsBytes = Base64.getDecoder().decode(descriptorBytesString);
            if (descriptorAsBytes == null) {
                throw new Exception("error decoding descriptor bytes");
            }
            FileDescriptorSet descriptorSet = FileDescriptorSet.parseFrom(descriptorAsBytes);
            FileDescriptor fileDescriptor = null;
            for (DescriptorProtos.FileDescriptorProto fdp : descriptorSet.getFileList()) {
                if (fdp.getName().equals(fileName)) {
                    fileDescriptor = FileDescriptor.buildFrom(fdp, new FileDescriptor[] {});
                    break;
                }
            }

            if (fileDescriptor == null) {
                throw new Exception("file not found");
            }

            for (Descriptors.Descriptor md : fileDescriptor.getMessageTypes()) {
                if (md.getName().equals(masterMsgName)) {
                    return md;
                }
            }
        } catch (Exception e) {
            handleError(String.format("compileMsgDescriptor: %s", e.getMessage()));
        }
        return null;
    }

    public void handleError(String msg) {

        if (brokerConnection != null && superstreamReady) {
            Map<String, String> envVars = System.getenv();
            String tags = envVars.get("SUPERSTREAM_TAGS");
            if (tags == null) {
                tags = "";
            }
            if (clientHash == "") {
                String message = String.format("[sdk: java][version: %s][tags: %s] %s", Consts.sdkVersion, tags, msg);
                brokerConnection.publish(Consts.superstreamErrorSubject, message.getBytes(StandardCharsets.UTF_8));
            } else {
                String message = String.format("[clientHash: %s][sdk: java][version: %s][tags: %s] %s",
                clientHash, Consts.sdkVersion, tags, msg);
                brokerConnection.publish(Consts.superstreamErrorSubject, message.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    public static Map<String, Object> normalizeClientConfig(Map<String, ?> javaConfig) {
        Map<String, Object> superstreamConfig = new HashMap<>();

        // Producer configurations
        // Note: Handling of `producer_return_errors` and `producer_return_successes` is
        // typically done programmatically in the Java client,
        // `producer_flush_max_messages` does not exist in java
        mapIfPresent(javaConfig, ProducerConfig.MAX_REQUEST_SIZE_CONFIG, superstreamConfig,
                "producer_max_messages_bytes");
        mapIfPresent(javaConfig, ProducerConfig.ACKS_CONFIG, superstreamConfig, "producer_required_acks");
        mapIfPresent(javaConfig, ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, superstreamConfig, "producer_timeout");
        mapIfPresent(javaConfig, ProducerConfig.RETRIES_CONFIG, superstreamConfig, "producer_retry_max");
        mapIfPresent(javaConfig, ProducerConfig.RETRY_BACKOFF_MS_CONFIG, superstreamConfig, "producer_retry_backoff");
        mapIfPresent(javaConfig, ProducerConfig.COMPRESSION_TYPE_CONFIG, superstreamConfig,
                "producer_compression_level");
        // Consumer configurations
        // Note: `consumer_return_errors`, `consumer_offsets_initial`,
        // `consumer_offsets_retry_max`, `consumer_group_rebalance_timeout`,
        // `consumer_group_rebalance_retry_max` does not exist in java
        mapIfPresent(javaConfig, ConsumerConfig.FETCH_MIN_BYTES_CONFIG, superstreamConfig, "consumer_fetch_min");
        mapIfPresent(javaConfig, ConsumerConfig.FETCH_MAX_BYTES_CONFIG, superstreamConfig, "consumer_fetch_default");
        mapIfPresent(javaConfig, ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, superstreamConfig, "consumer_retry_backoff");
        mapIfPresent(javaConfig, ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, superstreamConfig,
                "consumer_max_wait_time");
        mapIfPresent(javaConfig, ConsumerConfig.MAX_POLL_RECORDS_CONFIG, superstreamConfig,
                "consumer_max_processing_time");
        // mapIfPresent(javaConfig, ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
        // superstreamConfig, "consumer_offset_auto_commit_enable");
        // TODO: handle boolean vars
        mapIfPresent(javaConfig, ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, superstreamConfig,
                "consumer_offset_auto_commit_interval");
        mapIfPresent(javaConfig, ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, superstreamConfig,
                "consumer_group_session_timeout");
        mapIfPresent(javaConfig, ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, superstreamConfig,
                "consumer_group_heart_beat_interval");
        mapIfPresent(javaConfig, ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, superstreamConfig,
                "consumer_group_rebalance_retry_back_off");
        // mapIfPresent(javaConfig, ConsumerConfig.AUTO_OFFSET_RESET_CONFIG ,
        // superstreamConfig, "consumer_group_rebalance_reset_invalid_offsets"); //
        // TODO: handle boolean vars
        mapIfPresent(javaConfig, ConsumerConfig.GROUP_ID_CONFIG, superstreamConfig, "consumer_group_id");
        // Common configurations
        mapIfPresent(javaConfig, ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, superstreamConfig, "servers");
        // Note: No access to `producer_topics_partitions` and
        // `consumer_group_topics_partitions`
        return superstreamConfig;
    }

    private static void mapIfPresent(Map<String, ?> javaConfig, String javaKey, Map<String, Object> superstreamConfig,
            String superstreamKey) {
        if (javaConfig.containsKey(javaKey)) {
            if (javaKey == ProducerConfig.BOOTSTRAP_SERVERS_CONFIG) {
                Object value = javaConfig.get(javaKey);
                if (value instanceof String[]) {
                    superstreamConfig.put(superstreamKey, Arrays.toString((String[]) value));
                } else if (value instanceof ArrayList) {
                    @SuppressWarnings("unchecked")
                    ArrayList<String> arrayList = (ArrayList<String>) value;
                    superstreamConfig.put(superstreamKey, String.join(", ", arrayList));
                } else {
                    superstreamConfig.put(superstreamKey, value);
                }
            } else {
                superstreamConfig.put(superstreamKey, javaConfig.get(javaKey));
            }
        }
    }

    public static Map<String, Object> initSuperstreamConfig(Map<String, Object> configs, String type) {
        String isInnerConsumer = (String) configs.get(Consts.superstreamInnerConsumerKey);
        if (isInnerConsumer != null && isInnerConsumer.equals("true")) {
            return configs;
        }
        String interceptorToAdd = "";
        switch (type) {
            case "producer":
                interceptorToAdd = SuperstreamProducerInterceptor.class.getName();
                if (configs.containsKey(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)) {
                    if (!configs.containsKey(Consts.originalSerializer)) {
                        configs.put(Consts.originalSerializer,
                                configs.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
                        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                                SuperstreamSerializer.class.getName());
                    }
                }
                break;
            case "consumer":
                interceptorToAdd = SuperstreamConsumerInterceptor.class.getName();
                if (configs.containsKey(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)) {
                    if (!configs.containsKey(Consts.originalDeserializer)) {
                        configs.put(Consts.originalDeserializer,
                                configs.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
                        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                                SuperstreamDeserializer.class.getName());
                    }
                }
                break;
        }

        try {
            List<String> interceptors = null;
            Object existingInterceptors = configs.get(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG);
            if (interceptorToAdd != "") {
                if (existingInterceptors != null) {
                    if (existingInterceptors instanceof List) {
                        interceptors = new ArrayList<>((List<String>) existingInterceptors);
                    } else if (existingInterceptors instanceof String) {
                        interceptors = new ArrayList<>();
                        interceptors.add((String) existingInterceptors);
                    } else {
                        interceptors = new ArrayList<>();
                    }
                } else {
                    interceptors = new ArrayList<>();
                }
            }
            if (interceptorToAdd != "") {
                interceptors.add(interceptorToAdd);
                configs.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, interceptors);
            }

            Map<String, String> envVars = System.getenv();
            String superstreamHost = envVars.get("SUPERSTREAM_HOST");
            if (superstreamHost == null) {
                throw new Exception("host is required");
            }
            configs.put(Consts.superstreamHostKey, superstreamHost);
            String token = envVars.get("SUPERSTREAM_TOKEN");
            if (token == null) {
                token = Consts.superstreamDefaultToken;
            }
            configs.put(Consts.superstreamTokenKey, token);
            String learningFactorString = envVars.get("SUPERSTREAM_LEARNING_FACTOR");
            Integer learningFactor = Consts.superstreamDefaultLearningFactor;
            if (learningFactorString != null) {
                learningFactor = Integer.parseInt(learningFactorString);
            }
            configs.put(Consts.superstreamLearningFactorKey, learningFactor);
            Boolean reductionEnabled = false;
            String reductionEnabledString = envVars.get("SUPERSTREAM_REDUCTION_ENABLED");
            if (reductionEnabledString != null) {
                reductionEnabled = Boolean.parseBoolean(reductionEnabledString);
            }
            configs.put(Consts.superstreamReductionEnabledKey, reductionEnabled);
            String tags = envVars.get("SUPERSTREAM_TAGS");
            if (tags == null) {
                tags = "";
            }
            Superstream superstreamConnection = new Superstream(token, superstreamHost, learningFactor, configs,
                    reductionEnabled, type, tags);
            superstreamConnection.init();
            configs.put(Consts.superstreamConnectionKey, superstreamConnection);
        } catch (Exception e) {
            String errMsg = String.format("superstream: error initializing superstream: %s", e.getMessage());
            System.out.println(errMsg);
            switch (type) {
                case "producer":
                    if (configs.containsKey(Consts.originalSerializer)) {
                        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                                configs.get(Consts.originalSerializer));
                        configs.remove(Consts.originalSerializer);
                    }
                    break;
                case "consumer":
                    if (configs.containsKey(Consts.originalDeserializer)) {
                        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                                configs.get(Consts.originalDeserializer));
                        configs.remove(Consts.originalDeserializer);
                    }
                    break;
            }
        }
        return configs;
    }

    public static Properties initSuperstreamProps(Properties properties, String type) {
        String interceptors = (String) properties.get(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG);
        switch (type) {
            case "producer":
                if (interceptors != null && !interceptors.isEmpty()) {
                    interceptors += "," + SuperstreamProducerInterceptor.class.getName();
                } else {
                    interceptors = SuperstreamProducerInterceptor.class.getName();
                }
                if (properties.containsKey(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)) {
                    if (!properties.containsKey(Consts.originalSerializer)) {
                        properties.put(Consts.originalSerializer,
                                properties.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
                        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                                SuperstreamSerializer.class.getName());
                    }
                }
                break;
            case "consumer":
                if (interceptors != null && !interceptors.isEmpty()) {
                    interceptors += "," + SuperstreamConsumerInterceptor.class.getName();
                } else {
                    interceptors = SuperstreamConsumerInterceptor.class.getName();
                }
                if (properties.containsKey(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)) {
                    if (!properties.containsKey(Consts.originalDeserializer)) {
                        properties.put(Consts.originalDeserializer,
                                properties.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
                        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                                SuperstreamDeserializer.class.getName());
                    }
                }
                break;
        }
        if (interceptors != null) {
            properties.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, interceptors);
        }

        try {
            Map<String, String> envVars = System.getenv();
            String superstreamHost = envVars.get("SUPERSTREAM_HOST");
            if (superstreamHost == null) {
                throw new Exception("host is required");
            }
            properties.put(Consts.superstreamHostKey, superstreamHost);
            String token = envVars.get("SUPERSTREAM_TOKEN");
            if (token == null) {
                token = Consts.superstreamDefaultToken;
            }
            properties.put(Consts.superstreamTokenKey, token);
            String learningFactorString = envVars.get("SUPERSTREAM_LEARNING_FACTOR");
            Integer learningFactor = Consts.superstreamDefaultLearningFactor;
            if (learningFactorString != null) {
                learningFactor = Integer.parseInt(learningFactorString);
            }
            properties.put(Consts.superstreamLearningFactorKey, learningFactor);
            Boolean reductionEnabled = false;
            String reductionEnabledString = envVars.get("SUPERSTREAM_REDUCTION_ENABLED");
            if (reductionEnabledString != null) {
                reductionEnabled = Boolean.parseBoolean(reductionEnabledString);
            }
            properties.put(Consts.superstreamReductionEnabledKey, reductionEnabled);
            String tags = envVars.get("SUPERSTREAM_TAGS");
            if (tags != null) {
                properties.put(Consts.superstreamTagsKey, tags);
            }
            Map<String, Object> configs = propertiesToMap(properties);
            Superstream superstreamConnection = new Superstream(token, superstreamHost, learningFactor, configs,
                    reductionEnabled, type);
            superstreamConnection.init();
            properties.put(Consts.superstreamConnectionKey, superstreamConnection);
        } catch (Exception e) {
            String errMsg = String.format("superstream: error initializing superstream: %s", e.getMessage());
            System.out.println(errMsg);
        }
        return properties;
    }

    public static Map<String, Object> propertiesToMap(Properties properties) {
        return properties.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> String.valueOf(e.getKey()),
                        e -> e.getValue()));
    }

    public void updateTopicPartitions(String topic, Integer partition) {
        Set<Integer> partitions = topicPartitions.computeIfAbsent(topic, k -> new HashSet<>());
        if (!partitions.contains(partition)) {
            partitions.add(partition);
        }
    }
}
