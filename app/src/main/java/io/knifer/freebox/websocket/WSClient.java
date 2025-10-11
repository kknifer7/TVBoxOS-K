package io.knifer.freebox.websocket;

import com.github.tvbox.osc.util.LOG;
import com.google.common.collect.ImmutableList;
import com.google.gson.reflect.TypeToken;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import io.knifer.freebox.model.common.Message;
import io.knifer.freebox.util.CastUtil;
import io.knifer.freebox.util.GsonUtil;
import io.knifer.freebox.websocket.handler.WebSocketMessageHandler;
import io.knifer.freebox.websocket.handler.impl.DeleteMovieCollectionHandler;
import io.knifer.freebox.websocket.handler.impl.DeletePlayHistoryHandler;
import io.knifer.freebox.websocket.handler.impl.GetCategoryContentHandler;
import io.knifer.freebox.websocket.handler.impl.GetDetailContentHandler;
import io.knifer.freebox.websocket.handler.impl.GetHomeContentHandler;
import io.knifer.freebox.websocket.handler.impl.GetLivesHandler;
import io.knifer.freebox.websocket.handler.impl.GetMovieCollectedStatusHandler;
import io.knifer.freebox.websocket.handler.impl.GetMovieCollectionHandler;
import io.knifer.freebox.websocket.handler.impl.GetOnePlayHistoryHandler;
import io.knifer.freebox.websocket.handler.impl.GetPlayHistoryHandler;
import io.knifer.freebox.websocket.handler.impl.GetPlayerContentHandler;
import io.knifer.freebox.websocket.handler.impl.GetSearchContentHandler;
import io.knifer.freebox.websocket.handler.impl.GetSourceBeanListHandler;
import io.knifer.freebox.websocket.handler.impl.SaveMovieCollectionHandler;
import io.knifer.freebox.websocket.handler.impl.SavePlayHistoryHandler;
import io.knifer.freebox.websocket.service.WSService;

public class WSClient extends WebSocketClient {

    private final WSService service;

    private final List<WebSocketMessageHandler<?>> handlers;

    private final AtomicBoolean reconnectFlag;

    public WSClient(URI serverURI, String clientId) {
        super(serverURI);
        service = new WSService(this.getConnection(), clientId);
        handlers = ImmutableList.of(
                new GetSourceBeanListHandler(service),
                new GetHomeContentHandler(service),
                new GetCategoryContentHandler(service),
                new GetDetailContentHandler(service),
                new GetPlayerContentHandler(service),
                new GetPlayHistoryHandler(service),
                new GetSearchContentHandler(service),
                new SavePlayHistoryHandler(service),
                new DeletePlayHistoryHandler(service),
                new GetMovieCollectionHandler(service),
                new SaveMovieCollectionHandler(service),
                new DeleteMovieCollectionHandler(service),
                new GetOnePlayHistoryHandler(service),
                new GetMovieCollectedStatusHandler(service),
                new GetLivesHandler(service)
        );
        reconnectFlag = new AtomicBoolean(false);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        service.register();
        reconnectFlag.set(true);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        WebSocket connection;
        InetSocketAddress inetSocketAddress;

        LOG.i("closed with exit code " + code + " additional info: " + reason);
        if (!reconnectFlag.get()) {
            return;
        }
        // 异常断线后自动重连
        connection = this.getConnection();
        inetSocketAddress = connection.getLocalSocketAddress();
        WSHelper.connectBlocking(
                inetSocketAddress.getAddress().getHostAddress(),
                inetSocketAddress.getPort(),
                connection.hasSSLSupport()
        );
    }

    @Override
    public void onMessage(String message) {
        Message<Object> msgUnResolved = GsonUtil.fromJson(
                message, new TypeToken<Message<Object>>(){}
        );

        for (WebSocketMessageHandler<?> handler : handlers) {
            if (handler.support(msgUnResolved)) {
                handler.handle(CastUtil.cast(handler.resolve(message)));
            }
        }
    }

    @Override
    public void onMessage(ByteBuffer message) {
        System.out.println("received ByteBuffer");
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("an error occurred:" + ex);
    }

    @Override
    public void close() {
        reconnectFlag.set(false);
        super.close();
    }

    @Override
    public void closeBlocking() throws InterruptedException {
        reconnectFlag.set(false);
        super.closeBlocking();
    }
}