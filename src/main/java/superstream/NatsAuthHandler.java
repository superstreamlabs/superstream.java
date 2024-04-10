package superstream;

import io.nats.client.AuthHandler;
import io.nats.client.NKey;

public class NatsAuthHandler implements AuthHandler {
    private final String jwt;
    private final NKey nkey;

    public NatsAuthHandler(String jwt, String nkeySeed) {
        this.jwt = jwt;
        this.nkey = NKey.fromSeed(nkeySeed.toCharArray());
    }

    @Override
    public char[] getID() {
        return jwt.toCharArray();
    }

    @Override
    public byte[] sign(byte[] nonce) {
        try {
            return nkey.sign(nonce);
        } catch (Exception e) {
            // Handle signing error
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public char[] getJWT() {
        return jwt.toCharArray();
    }
}