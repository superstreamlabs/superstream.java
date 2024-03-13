package superstream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.Subscription;
import io.nats.client.api.ServerInfo;
import io.nats.client.ConnectionListener;
import io.nats.client.Consumer;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.MessageHandler;

public class SuperstreamConnection {
    public  Connection brokerConnection;
    public JetStream jetstream;
    public String superstreamJwt;
    public String superstreamNkey;
    public byte[] descriptorAsBytes;
    public Descriptors.Descriptor descriptor;
    public DynamicMessage.Builder messageBuilder;
    public String natsConnectionID;
    public int clientID;
    public String accountName;
    public int learningFactor = 20;
    public int learningFactorCounter = 0;
    public boolean learningRequestSent = false;
    private boolean isConsumer = false;
    private boolean isProducer = false;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private Subscription subscription;
    public int ProducerSchemaID = 0;
    public int ConsumerSchemaID = 0;
    public Map<String,?> config;
    public SuperstreamCounters clientCounters = new SuperstreamCounters();

   
    
    public SuperstreamConnection(String token, String host, Integer learnfactor, boolean isProd) {
        // String[] parts = token.split(":::");
        // if (parts.length != 2) {
        //     throw new IllegalArgumentException("Invalid token format");
        // }
        // superstreamJwt = parts[0];
        // superstreamNkey = parts[1];
        learningFactor = learnfactor;
        if (isProd) {
            isProducer = true;
        } else {
            isConsumer = true;
        }
        try {
            // DescriptorProto descriptorProto = DescriptorProto.parseFrom(descriptorAsBytes);
            // FileDescriptorProto fileDescriptorProto = FileDescriptorProto.newBuilder().addMessageType(descriptorProto).build();
            // FileDescriptor[] dependencies = {};
            // FileDescriptor fileDescriptor = FileDescriptor.buildFrom(fileDescriptorProto, dependencies);
            // if (fileDescriptor.getMessageTypes().isEmpty()) {
            //     throw new IllegalArgumentException("No message types found in the provided descriptor.");
            // } else {
            //     descriptor = fileDescriptor.getMessageTypes().get(0);
            // }
            // messageBuilder = DynamicMessage.newBuilder(descriptor);
            initializeNatsConnection(token, host);
            registerClient();
            subscribeToUpdates();
            reportClientsUpdate();
            if (isProd){
                sendClientTypeUpdateReq("producer");
            } else {
                sendClientTypeUpdateReq("consumer");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            brokerConnection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initializeNatsConnection(String token, String host) {
        try {
            // NatsAuthHandler ah = new NatsAuthHandler(superstreamJwt, superstreamNkey);
                Options options = new Options.Builder()
                        .server(host)
                        .userInfo(Consts.superstreamInternalUsername, token)
                        .maxReconnects(-1)
                        .reconnectWait(Duration.ofSeconds(1))
                        .connectionListener(new ConnectionListener() {
                            @Override
                            public void connectionEvent(Connection conn, Events type) {
                                if (type == Events.DISCONNECTED) {
                                    System.out.println("Disconnected from NATS");
                                } else if (type == Events.RECONNECTED) {
                                    try {
                                        natsConnectionID = generateNatsConnectionID();
                                        Map<String, Object> reqData = new HashMap<>();
                                        reqData.put("newNatsConnectiontId", natsConnectionID);
                                        reqData.put("clientId", clientID);
                                        ObjectMapper mapper = new ObjectMapper();
                                        byte[] reqBytes = mapper.writeValueAsBytes(reqData);
                                        brokerConnection.request(Consts.clientReconnectionUpdateSubject, reqBytes, Duration.ofSeconds(30));

                                    } catch (Exception e) {
                                        System.out.println("Failed to send reconnection update to server" + e.getMessage());
                                    }
                                    
                                    System.out.println("Reconnected to NATS");
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
            e.printStackTrace();
        }
    }

    private String generateNatsConnectionID() {
        ServerInfo serverInfo = brokerConnection.getServerInfo();
        String connectedServerName = serverInfo.getServerName();
        clientID = serverInfo.getClientId();
        return connectedServerName + ":" + clientID;
    }

    public void registerClient() {
        try {
            Map<String, Object> reqData = new HashMap<>();
            reqData.put("nats_connection_id", natsConnectionID);
            reqData.put("language", "java");
            reqData.put("learning_factor", learningFactor);
            reqData.put("version", Consts.sdkVersion);
            // reqData.put("config", "client_config");  // TODO: send client config
            ObjectMapper mapper = new ObjectMapper();
            byte[] reqBytes = mapper.writeValueAsBytes(reqData);
            Message reply = brokerConnection.request(Consts.clientRegisterSubject, reqBytes, Duration.ofSeconds(30));
            if (reply != null) {
                Map<String, Object> replyData = mapper.readValue(reply.getData(), Map.class);
                Object clientIDObject = replyData.get("client_id");
                if (clientIDObject instanceof Integer) {
                    clientID = (Integer) clientIDObject; // Directly cast if it's already an Integer
                } else if (clientIDObject instanceof String) {
                    try {
                        clientID = Integer.parseInt((String) clientIDObject);
                    } catch (NumberFormatException e) {
                        System.err.println("clientId is not a valid integer: " + clientIDObject);
                    }
                } else {
                    System.err.println("clientId is not a valid integer: " + clientIDObject);
                }
                Object accountNameObject = replyData.get("account_name");
                if (accountNameObject != null) {
                    accountName = accountNameObject.toString();
                } else {
                    System.err.println("accountName is not a valid string: " + accountNameObject);
                }
                Object learningFactorObject = replyData.get("client_id");
                if (learningFactorObject instanceof Integer) {
                    learningFactor = (Integer) learningFactorObject; // Directly cast if it's already an Integer
                } else if (learningFactorObject instanceof String) {
                    try {
                        learningFactor = Integer.parseInt((String) learningFactorObject);
                    } catch (NumberFormatException e) {
                        System.err.println("learningFactor is not a valid integer: " + learningFactorObject);
                    }
                } else {
                    System.err.println("learningFactor is not a valid integer: " + learningFactorObject);
                }
            } else {
                System.out.println("No reply received within the timeout period.");
            }
            // String subject = String.format("superstreamClientUpdatesSubject.%s", clientID);
            // Dispatcher dispatcher = brokerConnection.createDispatcher((msg) -> {
            //     try {
            //         String replySubject = msg.getReplyTo();
            //         String message = new String(msg.getData(), StandardCharsets.UTF_8);
            //         Map<String, Object> messageMap = mapper.readValue(message, Map.class);
            //         String clientType = (String) messageMap.get("clientType");
            //         int clientID = (int) messageMap.get("clientId");
            //         sendClientTypeUpdateReq(clientID, clientType);
            //     } catch (Exception e) {
            //         e.printStackTrace();
            //     }
            // });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendClientTypeUpdateReq(String clientType) {
        try {
            switch (clientType) {
                case "consumer":
                    isConsumer = true;
                    break;
            
                case "producer":
                    isProducer = true;
                    break;
            }
            Map<String, Object> reqData = new HashMap<>();
            reqData.put("clientId", clientID);
            reqData.put("clientType", clientType);
            ObjectMapper mapper = new ObjectMapper();
            byte[] reqBytes = mapper.writeValueAsBytes(reqData);
            brokerConnection.request(Consts.clientReconnectionUpdateSubject, reqBytes, Duration.ofSeconds(30));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // public void subscribeToUpdates() throws IOException, InterruptedException {
    //     String subject = String.format(Consts.superstreamClientUpdatesSubject, clientID);
    //     subscription = brokerConnection.subscribe(subject, this.subscriptionHandler());
    // }

    public void subscribeToUpdates() {
        try {
            String subject = Consts.superstreamUpdatesSubject + clientID;
            Dispatcher dispatcher = brokerConnection.createDispatcher(null);
            subscription = dispatcher.subscribe(subject, this.subscriptionHandler());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void reportClientsUpdate() {
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(() -> {
            try {
                byte[] byteCounters = objectMapper.writeValueAsBytes(clientCounters);

                brokerConnection.publish(String.format(Consts.superstreamClientsUpdateSubject, "counters", clientID), byteCounters);
                // brokerConnection.publish(String.format(superstreamClientsUpdateSubject, "config", clientID), byteConfig); // TODO: send config
            } catch (Exception e) {
                handleError("reportClientsUpdate: " + e.getMessage());
            }
        }, 0, 30, TimeUnit.SECONDS);
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

    // private void subscriptionHandler(Message msg) {
    //     try {
    //         byte[] msgBytes = msg.getData();
    //         Map<String, Object> message =  natsObjectMapper.readValue(msg.getData(), Map.class);
    //         String type = (String) message.get("type");
    //         switch (type) {
    //             case "LearnedSchema":
    //             Map<String, Object> payload =  natsObjectMapper.readValue((byte[]) message.get(payload), Map.class);
    //             byte[] descriptorAsBytes = (byte[]) payload.get("desc");
    //             DescriptorProto descriptorProto = DescriptorProto.parseFrom(descriptorAsBytes);
    //             FileDescriptorProto fileDescriptorProto = FileDescriptorProto.newBuilder().addMessageType(descriptorProto).build();
    //             FileDescriptor[] dependencies = {};
    //             FileDescriptor fileDescriptor = FileDescriptor.buildFrom(fileDescriptorProto, dependencies);
    //             if (fileDescriptor.getMessageTypes().isEmpty()) {
    //                 throw new IllegalArgumentException("No message types found in the provided descriptor.");
    //             } else {
    //                 descriptor = fileDescriptor.getMessageTypes().get(0);
    //             }
    //             messageBuilder = DynamicMessage.newBuilder(descriptor);

    //         }
    //         // message to json
    //     } catch (Exception e) {
    //         e.printStackTrace();
    //     }
    // }


    public byte[] jsonToProto(byte[] msgBytes) throws IOException {
        try {
            String jsonString = new String(msgBytes);
            JsonFormat.parser().merge(jsonString, messageBuilder);
            DynamicMessage message = messageBuilder.build();
            return message.toByteArray();
        } catch (Exception e) {
            if (e.getMessage().contains("Cannot find field")) {
                return msgBytes;
            }
        }
        return msgBytes;
    }

    public byte[] protoToJson(byte[] msgBytes) throws IOException {
        try {
            DynamicMessage message = DynamicMessage.parseFrom(descriptor, msgBytes);
            String jsonString = JsonFormat.printer().omittingInsignificantWhitespace().print(message);
            return jsonString.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            if (e.getMessage().contains("the input ended unexpectedly")) {
                return msgBytes;
            }
        }
        return msgBytes;
    }

    private MessageHandler subscriptionHandler() {
        return (msg) -> {
            try {
                Map<String, Object> update = objectMapper.readValue(msg.getData(), Map.class);
                processUpdate(update);
            } catch (IOException e) {
                handleError("SubscriptionHandler at json.Unmarshal: " + e.getMessage());
            }
        };
    }

    private void processUpdate(Map<String, Object> update) {
        if ("LearnedSchema".equals(update.get("type"))) {
            try {
                // Assuming update.get("payload") returns a JSON string or structure that can be directly converted
                byte[] payloadBytes = objectMapper.writeValueAsBytes(update.get("payload"));
                Map<String, Object> payload = objectMapper.readValue(payloadBytes, Map.class);
                byte[] descriptorAsBytes = (byte[]) payload.get("desc");
                if (descriptorAsBytes == null) {
                    throw new Exception("UpdatesHandler: error getting schema descriptor");
                }
                DescriptorProto descriptorProto = DescriptorProto.parseFrom(descriptorAsBytes);
                FileDescriptorProto fileDescriptorProto = FileDescriptorProto.newBuilder().addMessageType(descriptorProto).build();
                FileDescriptor[] dependencies = {};
                FileDescriptor fileDescriptor = FileDescriptor.buildFrom(fileDescriptorProto, dependencies);
                if (fileDescriptor.getMessageTypes().isEmpty()) {
                    throw new IllegalArgumentException("No message types found in the provided descriptor.");
                } else {
                    descriptor = fileDescriptor.getMessageTypes().get(0);
                }
                messageBuilder = DynamicMessage.newBuilder(descriptor);
                    ProducerSchemaID = (int) payload.get("schema_id");
            } catch (Exception e) {
                handleError(("processUpdate: " + e.getMessage()));
            }
        }
    }

    public void handleError(String msg) {
        String message = String.format("[account name: %s][clientID: %d][sdk: java][version: %s] %s", accountName, clientID, Consts.sdkVersion, msg);
        brokerConnection.publish(Consts.superstreamErrorSubject, message.getBytes(StandardCharsets.UTF_8));
    }
}

