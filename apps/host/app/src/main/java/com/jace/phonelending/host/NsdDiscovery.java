package com.jace.phonelending.host;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class NsdDiscovery {
    public static final String SERVICE_TYPE = "_phonelending._tcp.";

    private NsdDiscovery() {}

    public static PairingPayload.Endpoint resolve(Context context, String expectedServiceName, long timeoutMs) {
        if (expectedServiceName == null || expectedServiceName.isEmpty()) return null;
        NsdManager manager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        if (manager == null) return null;

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PairingPayload.Endpoint> result = new AtomicReference<>();
        NsdManager.DiscoveryListener[] holder = new NsdManager.DiscoveryListener[1];

        holder[0] = new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String serviceType) {}

            @Override public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (!expectedServiceName.equals(serviceInfo.getServiceName())) return;
                try {
                    manager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                        @Override public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {}

                        @Override public void onServiceResolved(NsdServiceInfo resolved) {
                            try {
                                if (resolved.getHost() != null && resolved.getPort() > 0) {
                                    result.compareAndSet(null, new PairingPayload.Endpoint(
                                            resolved.getHost().getHostAddress(), resolved.getPort()));
                                }
                            } finally {
                                latch.countDown();
                            }
                        }
                    });
                } catch (Throwable t) {
                    latch.countDown();
                }
            }

            @Override public void onServiceLost(NsdServiceInfo serviceInfo) {}
            @Override public void onDiscoveryStopped(String serviceType) {}
            @Override public void onStartDiscoveryFailed(String serviceType, int errorCode) { latch.countDown(); }
            @Override public void onStopDiscoveryFailed(String serviceType, int errorCode) {}
        };

        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, holder[0]);
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {
        } finally {
            try { manager.stopServiceDiscovery(holder[0]); } catch (Throwable ignored) {}
        }
        return result.get();
    }
}
