package superstream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

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
import io.nats.client.api.ServerInfo;
import io.nats.client.ConnectionListener;
import io.nats.client.Consumer;
import io.nats.client.JetStream;

public class SuperstreamConnection {
    private  Connection brokerConnection;
    private JetStream jetstream;
    private String superstreamJwt;
    private String superstreamNkey;
    public byte[] descriptorAsBytes;
    public Descriptors.Descriptor descriptor;
    public DynamicMessage.Builder messageBuilder;
    private String natsConnectionID;
    private int clientID;
    private static String clientReconnectionUpdateSubject  = "internal.clientReconnectionUpdate";
    
    public SuperstreamConnection(String token, String host, byte[] descriptorBytes) { // TODO: get descriptorAsBytes from be and remove from constructor
        descriptorAsBytes = descriptorBytes;
        String[] parts = token.split(":::");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid token format");
        }
        superstreamJwt = parts[0];
        superstreamNkey = parts[1];
        try {
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
            NatsAuthHandler ah = new NatsAuthHandler(superstreamJwt, superstreamNkey);
                Options options = new Options.Builder()
                        .server(host)
                        .authHandler(ah)
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
                                        brokerConnection.request(clientReconnectionUpdateSubject, reqBytes, Duration.ofSeconds(30));
                                    } catch (Exception e) {
                                        // TODO: handle exception
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

    public void close() {
        try {
            brokerConnection.close();
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
}
