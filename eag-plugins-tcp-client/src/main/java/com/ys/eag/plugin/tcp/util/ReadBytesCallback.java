package com.ys.eag.plugin.tcp.util;

/**
 * @author liuhualong
 * @date 2020/08/03
 */
public interface ReadBytesCallback {
    /**
     * 推送bytes
     *
     * @param bytes bytes
     * @param len   len
     */
    void pushBytes(byte[] bytes, int len);
}
