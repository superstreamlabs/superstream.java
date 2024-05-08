package ai.superstream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.Properties;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
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
    public  Connection brokerConnection;
    public JetStream jetstream;
    public String superstreamJwt;
    public String superstreamNkey;
    public byte[] descriptorAsBytes;
    public Descriptors.Descriptor descriptor;
    public String natsConnectionID;
    public int clientID;
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
    public ExecutorService executorService = Executors.newCachedThreadPool();

    public Superstream(String token, String host, Integer learningFactor, Map<String, Object> configs, Boolean enableReduction, String type) {
        this.learningFactor = learningFactor;
        this.token = token;
        this.host = host;
        this.configs = configs;
        this.reductionEnabled = enableReduction;
        this.type = type;
    }

    public void init() {
        try {
            initializeNatsConnection(token, host);
            registerClient(configs);
            subscribeToUpdates();
            reportClientsUpdate();
            sendClientTypeUpdateReq();
        } catch (Exception e) {
            handleError(e.getMessage());
        }
    }

    public void close() {
        try {
            if (brokerConnection != null) {
                brokerConnection.close();
            }
            executorService.shutdown();
        } catch (Exception e) {}
    }

    private void initializeNatsConnection(String token, String host) {
        try {
            Options options = new Options.Builder()
                    .server(host)
                    .userInfo(Consts.superstreamInternalUsername, token)
                    .maxReconnects(-1)
                    .reconnectWait(Duration.ofSeconds(1))
                    .connectionListener(new ConnectionListener() {
                        @Override
                        public void connectionEvent(Connection conn, Events type) {
                            if (type == Events.DISCONNECTED) {
                                System.out.println("superstream: Disconnected");
                            } else if (type == Events.RECONNECTED) {
                                try {
                                    natsConnectionID = generateNatsConnectionID();
                                    Map<String, Object> reqData = new HashMap<>();
                                    reqData.put("new_nats_connection_id", natsConnectionID);
                                    reqData.put("client_id", clientID);
                                    ObjectMapper mapper = new ObjectMapper();
                                    byte[] reqBytes = mapper.writeValueAsBytes(reqData);
                                    brokerConnection.request(Consts.clientReconnectionUpdateSubject, reqBytes, Duration.ofSeconds(30));
                                } catch (Exception e) {
                                    System.out.println("superstream: Failed to send reconnection update: " + e.getMessage());
                                }
                                System.out.println("superstream: Reconnected to superstream");
                            }
                        }
                    })
                    .build();

            Connection nc = Nats.connect(options);
            JetStream js = nc.jetStream();
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
        clientID = serverInfo.getClientId();
        return connectedServerName + ":" + clientID;
    }

    public void registerClient(Map<String, ?> configs) {
        try {
            Map<String, Object> reqData = new HashMap<>();
            reqData.put("nats_connection_id", natsConnectionID);
            reqData.put("language", "java");
            reqData.put("learning_factor", learningFactor);
            reqData.put("version", Consts.sdkVersion);
            reqData.put("config", normalizeClientConfig(configs));
            ObjectMapper mapper = new ObjectMapper();
            byte[] reqBytes = mapper.writeValueAsBytes(reqData);
            Message reply = brokerConnection.request(Consts.clientRegisterSubject, reqBytes, Duration.ofSeconds(30));
            if (reply != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> replyData = mapper.readValue(reply.getData(), Map.class);
                Object clientIDObject = replyData.get("client_id");
                if (clientIDObject instanceof Integer) {
                    clientID = (Integer) clientIDObject;
                } else if (clientIDObject instanceof String) {
                    try {
                        clientID = Integer.parseInt((String) clientIDObject);
                    } catch (NumberFormatException e) {
                        System.err.println("superstream: client_id is not a valid integer: " + clientIDObject);
                    }
                } else {
                    System.err.println("superstream: client_id is not a valid integer: " + clientIDObject);
                }
                Object accountNameObject = replyData.get("account_name");
                if (accountNameObject != null) {
                    accountName = accountNameObject.toString();
                } else {
                    System.err.println("superstream: account_name is not a valid string: " + accountNameObject);
                }
                Object learningFactorObject = replyData.get("learning_factor");
                if (learningFactorObject instanceof Integer) {
                    learningFactor = (Integer) learningFactorObject;
                } else if (learningFactorObject instanceof String) {
                    try {
                        learningFactor = Integer.parseInt((String) learningFactorObject);
                    } catch (NumberFormatException e) {
                        System.err.println("superstream: learning_factor is not a valid integer: " + learningFactorObject);
                    }
                } else {
                    System.err.println("superstream: learning_factor is not a valid integer: " + learningFactorObject);
                }
            } else {
                System.out.println("superstream: registering client: No reply received within the timeout period.");
            }
        } catch (Exception e) {
            System.out.println(String.format("superstream: %s", e.getMessage()));
        }
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
            reqData.put("client_id", clientID);
            reqData.put("type", type);
            ObjectMapper mapper = new ObjectMapper();
            byte[] reqBytes = mapper.writeValueAsBytes(reqData);
            brokerConnection.request(Consts.clientTypeUpdateSubject, reqBytes, Duration.ofSeconds(30));
        } catch (Exception e) {
           handleError(String.format("sendClientTypeUpdateReq: %s",e.getMessage()));
        }
    }

    public void subscribeToUpdates() {
        try {
            String subject = String.format(Consts.superstreamUpdatesSubject, clientID);
            Dispatcher dispatcher = brokerConnection.createDispatcher(this.updatesHandler());
            updatesSubscription = dispatcher.subscribe(subject, this.updatesHandler());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void reportClientsUpdate() {
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(() -> {
            try {
                byte[] byteCounters = objectMapper.writeValueAsBytes(clientCounters);
                Map<String, Object> topicPartitionConfig = new HashMap<>();
                if (!topicPartitions.isEmpty()) {
                    Map<String, Integer[]> topicPartitionsToSend = convertMap(topicPartitions);
                    switch(this.type) {
                        case "producer":
                        topicPartitionConfig.put("producer_topics_partitions", topicPartitionsToSend);
                        topicPartitionConfig.put("consumer_group_topics_partitions", new HashMap<String, Integer[]>());
                        break;
                        case "consumer":
                        topicPartitionConfig.put("producer_topics_partitions", new HashMap<String, Integer[]>());
                        topicPartitionConfig.put("consumer_group_topics_partitions", topicPartitionsToSend);
                        brokerConnection.publish(String.format(Consts.superstreamClientsUpdateSubject, "config", clientID), new byte[0]);
                        break;
                    }
                }
                byte[] byteConfig = objectMapper.writeValueAsBytes(topicPartitionConfig);
                brokerConnection.publish(String.format(Consts.superstreamClientsUpdateSubject, "counters", clientID), byteCounters);
                brokerConnection.publish(String.format(Consts.superstreamClientsUpdateSubject, "config", clientID), byteConfig);
            } catch (Exception e) {
                handleError("reportClientsUpdate: " + e.getMessage());
            }
        }, 0, 30, TimeUnit.SECONDS);
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
            brokerConnection.publish(String.format(Consts.superstreamLearningSubject, clientID), msg);
        } catch (Exception e) {
            handleError("sendLearningMessage: " + e.getMessage());
        }
    }

    public void sendRegisterSchemaReq() {
        try {
            brokerConnection.publish(String.format(Consts.superstreamRegisterSchemaSubject, clientID), new byte[0]);
            learningRequestSent = true;
        } catch (Exception e) {
            handleError("sendLearningMessage: " + e.getMessage());
        }
    }

    public byte[] jsonToProto(byte[] msgBytes) throws IOException {
        try {
            String jsonString = new String(msgBytes);
            DynamicMessage.Builder newMessageBuilder = DynamicMessage.newBuilder(descriptor);
            JsonFormat.parser().merge(jsonString, newMessageBuilder);
            DynamicMessage message = newMessageBuilder.build();
            return message.toByteArray();
        } catch (Exception e) {
            if (e.getMessage().contains("Cannot find field")) {
                return msgBytes;
            }
        }
        return msgBytes;
    }

    public byte[] protoToJson(byte[] msgBytes, Descriptors.Descriptor desc) throws IOException {
        try {
            DynamicMessage message = DynamicMessage.parseFrom(desc, msgBytes);
            String jsonString = JsonFormat.printer().omittingInsignificantWhitespace().print(message);
            return jsonString.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            if (e.getMessage().contains("the input ended unexpectedly")) {
                return msgBytes;
            }
        }
        return msgBytes;
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
            Message msg = brokerConnection.request(String.format(Consts.superstreamGetSchemaSubject, clientID),reqBytes, Duration.ofSeconds(30));
            if (msg == null) {
                throw new Exception("Could not get descriptor");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> respMap = objectMapper.readValue(new String(msg.getData(), StandardCharsets.UTF_8), Map.class);
            if (respMap.containsKey("desc") && respMap.get("desc") instanceof String) {
                String descriptorBytesString = (String) respMap.get("desc");
                String masterMsgName = (String) respMap.get("master_msg_name");
                String fileName = (String) respMap.get("file_name");
                Descriptors.Descriptor respDescriptor = compileMsgDescriptor(descriptorBytesString, masterMsgName, fileName);
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

    private Descriptors.Descriptor compileMsgDescriptor(String descriptorBytesString, String masterMsgName, String fileName) {
        try {
                byte[] descriptorAsBytes = Base64.getDecoder().decode(descriptorBytesString);
                if (descriptorAsBytes == null) {
                    throw new Exception("error decoding descriptor bytes");
                }
                FileDescriptorSet descriptorSet = FileDescriptorSet.parseFrom(descriptorAsBytes);
                FileDescriptor fileDescriptor = null;
            
                for (DescriptorProtos.FileDescriptorProto fdp : descriptorSet.getFileList()) {
                    if (fdp.getName().equals(fileName)) {
                        fileDescriptor = FileDescriptor.buildFrom(fdp, new FileDescriptor[]{});
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
            handleError(String.format("compileMsgDescriptor: %s",e.getMessage()));
        }
        return null;
    }

    public void handleError(String msg) {
        if (brokerConnection != null ) {
            String message = String.format("[account name: %s][clientID: %d][sdk: java][version: %s] %s", accountName, clientID, Consts.sdkVersion, msg);
            brokerConnection.publish(Consts.superstreamErrorSubject, message.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static Map<String, Object> normalizeClientConfig(Map<String, ?> javaConfig) {
        Map<String, Object> superstreamConfig = new HashMap<>();

        // Producer configurations
        // Note: Handling of `producer_return_errors` and `producer_return_successes` is typically done programmatically in the Java client, `producer_flush_max_messages` does not exist in java
        mapIfPresent(javaConfig, ProducerConfig.MAX_REQUEST_SIZE_CONFIG, superstreamConfig, "producer_max_messages_bytes"); 
        mapIfPresent(javaConfig, ProducerConfig.ACKS_CONFIG, superstreamConfig, "producer_required_acks"); 
        mapIfPresent(javaConfig, ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, superstreamConfig, "producer_timeout"); 
        mapIfPresent(javaConfig, ProducerConfig.RETRIES_CONFIG, superstreamConfig, "producer_retry_max"); 
        mapIfPresent(javaConfig, ProducerConfig.RETRY_BACKOFF_MS_CONFIG, superstreamConfig, "producer_retry_backoff"); 
        mapIfPresent(javaConfig, ProducerConfig.COMPRESSION_TYPE_CONFIG, superstreamConfig, "producer_compression_level");
        // Consumer configurations
        // Note: `consumer_return_errors`, `consumer_offsets_initial`, `consumer_offsets_retry_max`, `consumer_group_rebalance_timeout`, `consumer_group_rebalance_retry_max` does not exist in java
        mapIfPresent(javaConfig, ConsumerConfig.FETCH_MIN_BYTES_CONFIG, superstreamConfig, "consumer_fetch_min"); 
        mapIfPresent(javaConfig, ConsumerConfig.FETCH_MAX_BYTES_CONFIG, superstreamConfig, "consumer_fetch_default"); 
        mapIfPresent(javaConfig, ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, superstreamConfig, "consumer_retry_backoff"); 
        mapIfPresent(javaConfig, ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, superstreamConfig, "consumer_max_wait_time"); 
        mapIfPresent(javaConfig, ConsumerConfig.MAX_POLL_RECORDS_CONFIG, superstreamConfig, "consumer_max_processing_time"); 
        // mapIfPresent(javaConfig, ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, superstreamConfig, "consumer_offset_auto_commit_enable"); // TODO: handle boolean vars
        mapIfPresent(javaConfig, ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, superstreamConfig, "consumer_offset_auto_commit_interval");
        mapIfPresent(javaConfig, ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG , superstreamConfig, "consumer_group_session_timeout");
        mapIfPresent(javaConfig, ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG , superstreamConfig, "consumer_group_heart_beat_interval");
        mapIfPresent(javaConfig, ConsumerConfig.RETRY_BACKOFF_MS_CONFIG , superstreamConfig, "consumer_group_rebalance_retry_back_off");
        // mapIfPresent(javaConfig, ConsumerConfig.AUTO_OFFSET_RESET_CONFIG , superstreamConfig, "consumer_group_rebalance_reset_invalid_offsets"); // TODO: handle boolean vars
        mapIfPresent(javaConfig, ConsumerConfig.GROUP_ID_CONFIG , superstreamConfig, "consumer_group_id");
        // Common configurations
        mapIfPresent(javaConfig, ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, superstreamConfig, "servers"); 
        // Note: No access to `producer_topics_partitions` and `consumer_group_topics_partitions`
        return superstreamConfig;
    }

    private static void mapIfPresent(Map<String, ?> javaConfig, String javaKey, Map<String, Object> superstreamConfig, String superstreamKey) {
        if (javaConfig.containsKey(javaKey)) {
            superstreamConfig.put(superstreamKey, javaConfig.get(javaKey));
        }
    }

    public static Map<String, Object> initSuperstreamConfig(Map<String, Object> configs, String type) {
        String interceptors = (String) configs.get(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG);
        switch (type){ 
            case "producer":
                if (interceptors != null && !interceptors.isEmpty()) {
                    interceptors += "," + SuperstreamProducerInterceptor.class.getName();
                } else {
                    interceptors = SuperstreamProducerInterceptor.class.getName();
                }
                if (configs.containsKey(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)) {
                    if (!configs.containsKey(Consts.originalSerializer)) {
                        configs.put(Consts.originalSerializer, configs.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
                        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, SuperstreamSerializer.class.getName());
                    }
                }
                break;
            case "consumer":
                if (interceptors != null && !interceptors.isEmpty()) {
                    interceptors += "," + SuperstreamConsumerInterceptor.class.getName();
                } else {
                    interceptors = SuperstreamConsumerInterceptor.class.getName();
                }
                if (configs.containsKey(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)) {
                    if (!configs.containsKey(Consts.originalDeserializer)) {
                        configs.put(Consts.originalDeserializer, configs.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
                        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, SuperstreamDeserializer.class.getName());
                    }
                }
                break;
        }
        if (interceptors != null ) {
            configs.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, interceptors);
        }
        
        try {
            Map<String, String> envVars = System.getenv();
            String superstreamHost = envVars.get("SUPERSTREAM_HOST");
            if (superstreamHost == null) {
                throw new Exception("host is required");
            }
            configs.put(Consts.superstreamHostKey, superstreamHost);
            String token  = envVars.get("SUPERSTREAM_TOKEN");
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
            Superstream superstreamConnection = new Superstream(token, superstreamHost, learningFactor, configs, reductionEnabled, type);
            superstreamConnection.init();
            configs.put(Consts.superstreamConnectionKey, superstreamConnection);
        } catch (Exception e) {
            String errMsg = String.format("superstream: error initializing superstream: %s", e.getMessage());
            System.out.println(errMsg);
        }
        return configs;
    }

    public static Properties initSuperstreamProps(Properties properties, String type) {
        String interceptors = (String) properties.get(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG);
        switch (type){ 
            case "producer":
                if (interceptors != null && !interceptors.isEmpty()) {
                    interceptors += "," + SuperstreamProducerInterceptor.class.getName();
                } else {
                    interceptors = SuperstreamProducerInterceptor.class.getName();
                }
                if (properties.containsKey(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)) {
                    if (!properties.containsKey(Consts.originalSerializer)) {
                        properties.put(Consts.originalSerializer, properties.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
                        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, SuperstreamSerializer.class.getName());
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
                        properties.put(Consts.originalDeserializer, properties.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
                        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, SuperstreamDeserializer.class.getName());
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
            String token  = envVars.get("SUPERSTREAM_TOKEN");
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
            Map<String, Object> configs = propertiesToMap(properties);
            Superstream superstreamConnection = new Superstream(token, superstreamHost, learningFactor, configs, reductionEnabled, type);
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
                e -> e.getValue()
            ));
    }

    public void updateTopicPartitions(String topic, Integer partition) {
        Set<Integer> partitions = topicPartitions.computeIfAbsent(topic, k -> new HashSet<>());
        if (!partitions.contains(partition)) {
            partitions.add(partition);
        }
    }
}

