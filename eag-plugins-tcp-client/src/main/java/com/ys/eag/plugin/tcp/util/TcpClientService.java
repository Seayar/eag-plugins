package com.ys.eag.plugin.tcp.util;

import java.util.concurrent.Executor;

/**
 * @author liuhualong
 * @date 2020/08/03
 */
public interface TcpClientService {


    /**
     * 连接状态
     *
     * @return 连接状态
     */
    boolean getConnectStatus();

    /**
     * 创建连接
     *
     * @param ip      ip
     * @param port    port
     * @param timeout 连接超时
     * @return 结果
     */
    boolean connect(String ip, int port, int timeout);

    /**
     * 断开连接
     */
    void disconnect();

    /**
     * 添加读字节监听
     *
     * @param maxBytesSize      最大可能的字节数
     * @param executor          线程池
     * @param readBytesCallback 字节监听回调
     */
    void addBytesListener(int maxBytesSize, Executor executor, ReadBytesCallback readBytesCallback);

    /**
     * 写字节流
     *
     * @param bytes 写值
     * @return 结果
     */
    boolean writeBytes(byte[] bytes);
}
