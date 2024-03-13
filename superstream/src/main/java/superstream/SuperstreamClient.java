package superstream;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.DynamicMessage;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Message;

public class SuperstreamClient {
    public int clientID;
    public String accountName;
    public String natsConnectionID;
    public boolean isConsumer = false;
    public boolean isProducer = false;
    public int learningFactor = 0;
    public int learningFactorCounter = 0;
    public boolean learningFactorSent = false;
    public boolean getSchemaRequestSent = false;
    public SuperstreamConnection superstreamConnection;
    // public  Connection brokerConnection;
    // public JetStream jetstream;
    public Map<String, DynamicMessage.Builder> producerProtoDescMap = new HashMap<>();
    public Map<String, String> producerSchemaIDMap = new HashMap<>();
    public Map<String, DynamicMessage.Builder> consumerProtoDescMap = new HashMap<>();
    public Map<String, String> consumerSchemaIDMap = new HashMap<>();
    public ClientCounters clientCounters = new ClientCounters();
    public Map<String, Object> clientConfig = new HashMap<>();

    public SuperstreamClient(SuperstreamConnection conn) {
        superstreamConnection = conn;
        clientID = 0;
        accountName = "";
        natsConnectionID = "";
        isConsumer = false;
        isProducer = false;
        learningFactor = 0;
        learningFactorCounter = 0;
        learningFactorSent = false;
        getSchemaRequestSent = false;
        superstreamConnection = null;
        // brokerConnection = null;
        // jetstream = null;
        producerProtoDescMap = null;
        producerSchemaIDMap = null;
        consumerProtoDescMap = null;
        consumerSchemaIDMap = null;
        clientCounters = new ClientCounters();
        clientConfig = null;
        registerClient();
    }

    @SuppressWarnings("unchecked")
    private void registerClient() {
        try {
            Map<String, Object> reqData = new HashMap<>();
            reqData.put("nats_connection_id", natsConnectionID);
            reqData.put("language", "java");
            reqData.put("learning_factor", superstreamConnection.learningFactor);
            reqData.put("clientId", clientID);
            reqData.put("version", Consts.sdkVersion);
            // reqData.put("config", "client_config");  // TODO: send client config
            ObjectMapper mapper = new ObjectMapper();
            byte[] reqBytes = mapper.writeValueAsBytes(reqData);
            Message reply = superstreamConnection.brokerConnection.request(Consts.clientRegisterSubject, reqBytes, Duration.ofSeconds(30));
            if (reply != null) {
                Map<String, Object> replyData = mapper.readValue(reply.getData(), Map.class);
                Object clientIDObject = replyData.get("client_id");
                int clientIDRes = 0;
                if (clientIDObject instanceof Integer) {
                    clientIDRes = (Integer) clientIDObject; // Directly cast if it's already an Integer
                } else if (clientIDObject instanceof String) {
                    try {
                        clientIDRes = Integer.parseInt((String) clientIDObject);
                    } catch (NumberFormatException e) {
                        System.err.println("clientId is not a valid integer: " + clientIDObject);
                    }
                } else {
                    System.err.println("clientId is not a valid integer: " + clientIDObject);
                }
                clientID = clientIDRes;
                String accountNameRes = null;
                Object accountNameObject = replyData.get("account_name");
                if (accountNameObject != null) {
                    accountNameRes = accountNameObject.toString();
                } else {
                    System.err.println("accountName is not a valid string: " + accountNameObject);
                }
                accountName = accountNameRes;
                Object learningFactorObject = replyData.get("client_id");
                int learningFactorRes = 0;
                if (learningFactorObject instanceof Integer) {
                    learningFactorRes = (Integer) learningFactorObject; // Directly cast if it's already an Integer
                } else if (learningFactorObject instanceof String) {
                    try {
                        learningFactorRes = Integer.parseInt((String) learningFactorObject);
                    } catch (NumberFormatException e) {
                        System.err.println("learningFactor is not a valid integer: " + learningFactorObject);
                    }
                } else {
                    System.err.println("learningFactor is not a valid integer: " + learningFactorObject);
                }
                learningFactor = learningFactorRes;
            } else {
                System.out.println("No reply received within the timeout period.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public class ClientCounters {
        public long totalBytesBeforeReduction;
        public long totalBytesAfterReduction;
        public int totalMessagesSuccessfullyProduce;
        public int totalMessagesSuccessfullyConsumed;
        public int totalMessagesFailedProduce;
        public int totalMessagesFailedConsume;
        
        public ClientCounters() {
            totalBytesBeforeReduction = 0;
            totalBytesAfterReduction = 0;
            totalMessagesSuccessfullyProduce = 0;
            totalMessagesSuccessfullyConsumed = 0;
            totalMessagesFailedProduce = 0;
            totalMessagesFailedConsume = 0;
        }

        public void reset() {
            totalBytesBeforeReduction = 0;
            totalBytesAfterReduction = 0;
            totalMessagesSuccessfullyProduce = 0;
            totalMessagesSuccessfullyConsumed = 0;
            totalMessagesFailedProduce = 0;
            totalMessagesFailedConsume = 0;
        }

        public void incrementTotalBytesBeforeReduction(long bytes) {
            totalBytesBeforeReduction += bytes;
        }

        public void incrementTotalBytesAfterReduction(long bytes) {
            totalBytesAfterReduction += bytes;
        }

        public void incrementTotalMessagesSuccessfullyProduce() {
            totalMessagesSuccessfullyProduce++;
        }

        public void incrementTotalMessagesSuccessfullyConsumed() {
            totalMessagesSuccessfullyConsumed++;
        }

        public void incrementTotalMessagesFailedProduce() {
            totalMessagesFailedProduce++;
        }

        public void incrementTotalMessagesFailedConsume() {
            totalMessagesFailedConsume++;
        }
    }
}
