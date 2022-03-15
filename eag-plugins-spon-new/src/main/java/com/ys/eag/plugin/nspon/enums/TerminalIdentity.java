package com.ys.eag.plugin.nspon.enums;

/**
 * @author liuhualong
 * @date 2020/08/21
 */
public enum TerminalIdentity {

    /**
     * 接收方
     */
    RECEIVER(0),
    /**
     * 发起方
     */
    SPONSOR(1);

    public int id;

    TerminalIdentity(int id) {
        this.id = id;
    }

    public static TerminalIdentity get(int id) {
        if (id == 0) {
            return RECEIVER;
        }
        if (id == 1) {
            return SPONSOR;
        }
        return null;
    }
}
