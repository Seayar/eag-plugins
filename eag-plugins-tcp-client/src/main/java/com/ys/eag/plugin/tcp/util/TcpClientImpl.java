package com.ys.eag.plugin.tcp.util;

import lombok.extern.slf4j.Slf4j;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executor;

/**
 * @author liuhualong
 * @date 2020/08/03
 */
@Slf4j
public class TcpClientImpl implements TcpClientService {
    private Socket socket;
    private DataInputStream dis;
    private OutputStream os;

    @Override
    public boolean getConnectStatus() {
        return connectStatus & socket.isConnected();
    }

    private Boolean connectStatus;

    @Override
    public boolean connect(String ip, int port, int timeout) {
        try {
            socket = new Socket();
            socket.setReuseAddress(true);
            socket.connect(new InetSocketAddress(ip, port), timeout);

            dis = new DataInputStream(socket.getInputStream());
            os = socket.getOutputStream();
        } catch (IOException e) {
            log.info("TCP连接失败", e);
            return false;
        }
        return connectStatus = true;
    }

    @Override
    public void disconnect() {
        try {
            if (dis != null) {
                dis.close();
            }
            if (os != null) {
                os.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        connectStatus = false;
    }

    @Override
    public void addBytesListener(int maxBytesSize, Executor executor, ReadBytesCallback readBytesCallback) {
        executor.execute(() -> {
            while (socket.isConnected() && !Thread.currentThread().isInterrupted()) {
                try {
                    byte[] bytes = new byte[maxBytesSize];
                    int read = dis.read(bytes);
                    readBytesCallback.pushBytes(bytes, read);
                } catch (IOException e) {
                    log.info("TCP监听过程中发生异常", e);
                    Thread.currentThread().interrupt();
                    connectStatus = false;
                }
            }
        });
    }

    @Override
    public boolean writeBytes(byte[] bytes) {
        if (socket.isClosed()) {
            return false;
        }
        try {
            os.write(bytes);
        } catch (IOException e) {
            log.info("TCP写字节流时发生异常", e);
            return connectStatus = false;
        }
        return true;
    }


}
