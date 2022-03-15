package com.ys.eag.plugin.nspon.enums;

/**
 * @author liuhualong
 * @date 2020/08/21
 */
public enum TerminalState {
    /**
     * 终端状态
     */
    OFFLINE("offline", -1),
    ONLINE("online", 0),
    TALKING("talking", 1),
    LISTENING("listening", 2),
    BROADCASTING("broadcasting", 3),
    MEETING("meeting", 4);

    public String index;
    public int state;

    TerminalState(String index, int state) {
        this.index = index;
        this.state = state;
    }

    public static TerminalState get(int state) {
        for (TerminalState ts : TerminalState.values()) {
            if (ts.state == state) {
                return ts;
            }
        }
        return null;
    }
}
