package com.jace.phonelending.consumer;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NetworkUtils {
    private NetworkUtils() {}

    public static List<String> localEndpointHints(int port) {
        ArrayList<String> result = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (java.net.InetAddress address : Collections.list(ni.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress() && address.isSiteLocalAddress()) {
                        result.add(address.getHostAddress() + ":" + port);
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }
}
