package ru.serega6531.packmate.service.optimization;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.tls.ExporterLabel;
import org.bouncycastle.tls.PRFAlgorithm;
import org.bouncycastle.tls.crypto.TlsSecret;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsSecret;
import org.pcap4j.util.ByteArrays;
import ru.serega6531.packmate.model.Packet;
import ru.serega6531.packmate.service.optimization.tls.TlsPacket;
import ru.serega6531.packmate.service.optimization.tls.keys.TlsKeyUtils;
import ru.serega6531.packmate.service.optimization.tls.numbers.CipherSuite;
import ru.serega6531.packmate.service.optimization.tls.numbers.ContentType;
import ru.serega6531.packmate.service.optimization.tls.numbers.HandshakeType;
import ru.serega6531.packmate.service.optimization.tls.records.ApplicationDataRecord;
import ru.serega6531.packmate.service.optimization.tls.records.HandshakeRecord;
import ru.serega6531.packmate.service.optimization.tls.records.handshakes.BasicRecordContent;
import ru.serega6531.packmate.service.optimization.tls.records.handshakes.ClientHelloHandshakeRecordContent;
import ru.serega6531.packmate.service.optimization.tls.records.handshakes.HandshakeRecordContent;
import ru.serega6531.packmate.service.optimization.tls.records.handshakes.ServerHelloHandshakeRecordContent;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.interfaces.RSAPrivateKey;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class TlsDecryptor {

    private static final Pattern cipherSuitePattern = Pattern.compile("TLS_RSA_WITH_([A-Z0-9_]+)_([A-Z0-9]+)");

    private final List<Packet> packets;
    private final RsaKeysHolder keysHolder;

    @SneakyThrows
    public void decryptTls() {
        ListMultimap<Packet, TlsPacket.TlsHeader> tlsPackets = ArrayListMultimap.create(packets.size(), 1);

        packets.forEach(p -> tlsPackets.putAll(p, createTlsHeaders(p)));

        ClientHelloHandshakeRecordContent clientHello = (ClientHelloHandshakeRecordContent)
                getHandshake(tlsPackets.values(), HandshakeType.CLIENT_HELLO).orElseThrow();
        ServerHelloHandshakeRecordContent serverHello = (ServerHelloHandshakeRecordContent)
                getHandshake(tlsPackets.values(), HandshakeType.SERVER_HELLO).orElseThrow();

        byte[] clientRandom = clientHello.getRandom();
        byte[] serverRandom = serverHello.getRandom();

        CipherSuite cipherSuite = serverHello.getCipherSuite();

        if (cipherSuite.name().startsWith("TLS_RSA_WITH_")) {
            Matcher matcher = cipherSuitePattern.matcher(cipherSuite.name());
            //noinspection ResultOfMethodCallIgnored
            matcher.find();
            String blockCipher = matcher.group(1);
            String hashAlgo = matcher.group(2);

            //TODO
            RSAPrivateKey privateKey = keysHolder.getKey(null);
            if(privateKey == null) {
                log.warn("Key for modulus not found: {}", "TODO");
                return;
            }

            BasicRecordContent clientKeyExchange = (BasicRecordContent)
                    getHandshake(tlsPackets.values(), HandshakeType.CLIENT_KEY_EXCHANGE).orElseThrow();

            byte[] encryptedPreMaster = TlsKeyUtils.getClientRsaPreMaster(clientKeyExchange.getContent(), 0);

            Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            rsa.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] preMaster = rsa.doFinal(encryptedPreMaster);
            byte[] randomCS = ArrayUtils.addAll(clientRandom, serverRandom);
            byte[] randomSC = ArrayUtils.addAll(serverRandom, clientRandom);

            BcTlsSecret preSecret = new BcTlsSecret(new BcTlsCrypto(null), preMaster);
            TlsSecret masterSecret = preSecret.deriveUsingPRF(
                    PRFAlgorithm.tls_prf_sha256, ExporterLabel.master_secret, randomCS, 48);
            byte[] expanded = masterSecret.deriveUsingPRF(
                    PRFAlgorithm.tls_prf_sha256, ExporterLabel.key_expansion, randomSC, 136).extract(); // для sha256

            byte[] clientMacKey = new byte[20];
            byte[] serverMacKey = new byte[20];
            byte[] clientEncryptionKey = new byte[32];
            byte[] serverEncryptionKey = new byte[32];
            byte[] clientIV = new byte[16];
            byte[] serverIV = new byte[16];

            ByteBuffer bb = ByteBuffer.wrap(expanded);
            bb.get(clientMacKey);
            bb.get(serverMacKey);
            bb.get(clientEncryptionKey);
            bb.get(serverEncryptionKey);
            bb.get(clientIV);
            bb.get(serverIV);

            byte[] clientFinishedEncrypted = getFinishedData(tlsPackets, true);
            byte[] serverFinishedEncrypted = getFinishedData(tlsPackets, false);

            Cipher clientCipher = createCipher(clientEncryptionKey, clientIV, clientFinishedEncrypted);
//            byte[] clientFinishedData = clientCipher.update(clientFinishedEncrypted);
//            HandshakeRecord clientFinished = HandshakeRecord.newInstance(clientFinishedData, 16, clientFinishedData.length - 16);

            Cipher serverCipher = createCipher(serverEncryptionKey, serverIV, serverFinishedEncrypted);
//            byte[] serverFinishedData = serverCipher.update(serverFinishedEncrypted);
//            HandshakeRecord serverFinished = HandshakeRecord.newInstance(serverFinishedData, 16, serverFinishedData.length - 16);

            for (Map.Entry<Packet, TlsPacket.TlsHeader> entry : tlsPackets.entries()) {
                if (entry.getValue().getContentType() == ContentType.APPLICATION_DATA) {
                    byte[] data = ((ApplicationDataRecord) entry.getValue().getRecord()).getData();
                    boolean client = entry.getKey().isIncoming();

                    byte[] decoded;

                    if(client) {
                        decoded = clientCipher.update(data);
                    } else {
                        decoded = serverCipher.update(data);
                    }

                    decoded = clearDecodedData(decoded);
                    String string = new String(decoded);
                    System.out.println(string);
                }
            }
        }

    }

    @SneakyThrows
    private Cipher createCipher(byte[] key, byte[] iv, byte[] initData) {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");  // TLS_RSA_WITH_AES_256_CBC_SHA
        SecretKeySpec serverSkeySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec serverIvParameterSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, serverSkeySpec, serverIvParameterSpec);
        cipher.update(initData);

        return cipher;
    }

    private byte[] clearDecodedData(byte[] decoded) {
        int start = 32;
        int end = decoded.length - 6; //FIXME
        decoded = ByteArrays.getSubArray(decoded, start, end - start);
        return decoded;
    }

    private byte[] getFinishedData(ListMultimap<Packet, TlsPacket.TlsHeader> tlsPackets, boolean incoming) {
        return  ((BasicRecordContent) getHandshake(tlsPackets.asMap().entrySet().stream()
                .filter(ent -> ent.getKey().isIncoming() == incoming)
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream), HandshakeType.ENCRYPTED_HANDSHAKE_MESSAGE))
                .getContent();
    }

    private HandshakeRecordContent getHandshake(Stream<TlsPacket.TlsHeader> stream, HandshakeType handshakeType) {
        return stream.filter(p -> p.getContentType() == ContentType.HANDSHAKE)
                .map(p -> ((HandshakeRecord) p.getRecord()))
                .filter(r -> r.getHandshakeType() == handshakeType)
                .map(r -> ((BasicRecordContent) r.getContent()))
                .findFirst()
                .orElseThrow();
    }

    private Optional<HandshakeRecordContent> getHandshake(Collection<TlsPacket.TlsHeader> packets,
                                                          HandshakeType handshakeType) {
        return packets.stream()
                .filter(p -> p.getContentType() == ContentType.HANDSHAKE)
                .map(p -> ((HandshakeRecord) p.getRecord()))
                .filter(r -> r.getHandshakeType() == handshakeType)
                .map(HandshakeRecord::getContent)
                .findFirst();
    }

    @SneakyThrows
    private List<TlsPacket.TlsHeader> createTlsHeaders(Packet p) {
        List<TlsPacket.TlsHeader> headers = new ArrayList<>();
        TlsPacket tlsPacket = TlsPacket.newPacket(p.getContent(), 0, p.getContent().length);

        headers.add(tlsPacket.getHeader());

        while (tlsPacket.getPayload() != null) {
            tlsPacket = (TlsPacket) tlsPacket.getPayload();
            headers.add(tlsPacket.getHeader());
        }

        return headers;
    }

}
