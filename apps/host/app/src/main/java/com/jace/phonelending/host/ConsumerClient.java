package com.jace.phonelending.host;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ConsumerClient {
    public static final class PairResult {
        public boolean ok;
        public String error;
        public DeviceRecord device;
    }

    public static final class CommandResult {
        public boolean transportOk;
        public boolean accepted;
        public String commandId;
        public String state;
        public long remaining;
        public String message;
    }

    public PairResult pair(String hostId, String alias, String ip, int port, String code) {
        PairResult result = new PairResult();
        try {
            String response = exchange(ip, port, "PAIR|" + hostId + "|" + code);
            String[] p = response.split("\\|", -1);
            if (p.length >= 5 && "PAIRED".equals(p[0])) {
                DeviceRecord d = new DeviceRecord();
                d.deviceId = p[1];
                d.alias = alias == null || alias.trim().isEmpty() ? d.deviceId : alias.trim();
                d.ip = ip.trim();
                d.port = port;
                d.state = p[2];
                d.remainingSeconds = Long.parseLong(p[3]);
                d.protocolVersion = Integer.parseInt(p[4]);
                d.lastSyncEpoch = System.currentTimeMillis();
                d.sharedKey = CryptoUtils.sha256(d.deviceId + "|" + code + "|" + hostId);
                d.lastMessage = "paired";
                result.ok = true;
                result.device = d;
            } else {
                result.error = response;
            }
        } catch (Exception e) {
            result.error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        return result;
    }

    public CommandResult command(DeviceRecord device, String command, String payload) {
        CommandResult result = new CommandResult();
        result.commandId = UUID.randomUUID().toString();
        try {
            long timestamp = System.currentTimeMillis();
            String signed = result.commandId + "|" + timestamp + "|" + command + "|" + payload;
            String signature = CryptoUtils.hmacBase64(device.sharedKey, signed);
            String line = "CMD|" + signed + "|" + signature;
            String response = exchange(device.ip, device.port, line);
            String[] p = response.split("\\|", -1);
            result.transportOk = true;
            if (p.length >= 6 && "ACK".equals(p[0])) {
                result.accepted = "OK".equals(p[2]);
                result.state = p[3];
                result.remaining = Long.parseLong(p[4]);
                result.message = p[5];
            } else {
                result.accepted = false;
                result.message = response;
            }
        } catch (Exception e) {
            result.transportOk = false;
            result.accepted = false;
            result.message = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        return result;
    }

    private String exchange(String ip, int port, String line) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), 4500);
            socket.setSoTimeout(7000);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out.write(line);
            out.write("\n");
            out.flush();
            String response = in.readLine();
            if (response == null) throw new IllegalStateException("No response from Consumer");
            return response.trim();
        }
    }
}
