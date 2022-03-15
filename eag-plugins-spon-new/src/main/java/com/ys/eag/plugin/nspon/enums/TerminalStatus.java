package com.ys.eag.plugin.nspon.enums;

/**
 * @author liuhualong
 * @date 2020/08/21
 */
public class TerminalStatus {
    public String source;
    public TerminalIdentity identity;
    public TerminalState state;
    public String target;

    public TerminalStatus(String source, TerminalState state) {
        this.source = source;
        this.state = state;
    }

    public TerminalStatus(String source, TerminalIdentity identity, TerminalState state, String target) {
        this.source = source;
        this.identity = identity;
        this.state = state;
        this.target = target;
    }
}
