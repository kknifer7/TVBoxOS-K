package io.knifer.freebox.websocket;

import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.orhanobut.hawk.Hawk;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * FreeBox WebSocket Helper
 * @author Knifer
 */
public final class WSHelper {

    private static WSClient client;

    private static volatile boolean initFlag = false;

    private static volatile String clientId;

    public static boolean init(String clientId) {
        if (initFlag) {
            return false;
        }

        String address = Hawk.get(HawkConfig.FREE_BOX_SERVICE_ADDRESS);
        Integer port = Hawk.get(HawkConfig.FREE_BOX_SERVICE_PORT);

        WSHelper.clientId = clientId;
        initFlag = true;
        if (address != null && port != null) {
            // 目前只支持ws协议
            connect(address, port, false);
        }
        LOG.i("WSHelper init successfully");

        return true;
    }

    private static void createClient(String address, int port, boolean safeFlag) {
        try {
            client = new WSClient(
                    new URI(safeFlag ? "wss" : "ws" + "://" + address + ":" + port),
                    clientId
            );
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static void connect(String address, int port, boolean safeFlag) {
        if (!initFlag) {
            throw new IllegalStateException("WSHelper not init");
        }
        if (isOpen()) {
            throw new IllegalStateException("WebSocket Service already connected");
        }
        createClient(address, port, safeFlag);
        client.connect();
    }

    public static boolean connectBlocking(String address, int port, boolean safeFlag) {
        if (!initFlag) {
            LOG.e("WSHelper not init");

            return false;
        }
        if (isOpen()) {
            LOG.e("WebSocket Service already connected");

            return false;
        }
        createClient(address, port, safeFlag);
        try {
            return client.connectBlocking();
        } catch (Exception e) {
            LOG.e("unknown exception: " + e.getMessage());
            return false;
        }
    }

    public static boolean isOpen() {
        return client != null && client.isOpen();
    }

    public static boolean isClosed() {
        return client != null && client.isClosed();
    }

    public static boolean isClosing() {
        return client != null && client.isClosing();
    }

    public static void close() {
        if (canClose()) {
            client.close();
        }
    }

    public static void closeBlocking() {
        if (canClose()) {
            try {
                client.closeBlocking();
            } catch (InterruptedException ignored) {}
        }
    }

    private static boolean canClose() {
        return client != null && !client.isClosed() && !client.isClosing();
    }
}
