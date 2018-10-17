package org.apache.rocketmq.dleger.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.rocketmq.dleger.DLegerConfig;
import org.apache.rocketmq.dleger.entry.DLegerEntry;
import org.apache.rocketmq.dleger.MemberState;
import org.apache.rocketmq.dleger.protocol.DLegerResponseCode;
import org.apache.rocketmq.dleger.utils.PreConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DLegerMemoryStore extends DLegerStore {

    private static Logger logger = LoggerFactory.getLogger(DLegerMemoryStore.class);

    private long legerBeginIndex = -1;
    private long legerEndIndex = -1;
    private long committedIndex = -1;
    private long legerEndTerm;
    private Map<Long, DLegerEntry> cachedEntries = new ConcurrentHashMap<>();

    private DLegerConfig dLegerConfig;
    private MemberState memberState;


    public DLegerMemoryStore(DLegerConfig dLegerConfig, MemberState memberState) {
        this.dLegerConfig = dLegerConfig;
        this.memberState =  memberState;
    }

    @Override
    public long appendAsLeader(DLegerEntry entry) {
        PreConditions.check(memberState.isLeader(), DLegerResponseCode.NOT_LEADER);
        synchronized (memberState) {
            PreConditions.check(memberState.isLeader(), DLegerResponseCode.NOT_LEADER);
            legerEndIndex++;
            committedIndex++;
            legerEndTerm = memberState.currTerm();
            entry.setIndex(legerEndIndex);
            entry.setTerm(memberState.currTerm());
            if (logger.isDebugEnabled()) {
                logger.debug("[{}] Append as Leader {} {}", memberState.getSelfId(), entry.getIndex(), entry.getBody().length);
            }
            cachedEntries.put(entry.getIndex(), entry);
            if (legerBeginIndex == -1) {
                legerBeginIndex = legerEndIndex;
            }
            return entry.getIndex();
        }
    }


    @Override
    public long appendAsFollower(DLegerEntry entry, long leaderTerm, String leaderId) {
        PreConditions.check(memberState.isFollower(), DLegerResponseCode.NOT_FOLLOWER);
        synchronized(memberState) {
            PreConditions.check(memberState.isFollower(), DLegerResponseCode.NOT_FOLLOWER);
            PreConditions.check(leaderTerm == memberState.currTerm(), DLegerResponseCode.INCONSISTENT_TERM);
            PreConditions.check(leaderId.equals(memberState.getLeaderId()), DLegerResponseCode.INCONSISTENT_LEADER);
            if (logger.isDebugEnabled()) {
                logger.debug("[{}] Append as Follower {} {}", memberState.getSelfId(), entry.getIndex(), entry.getBody().length);
            }
            legerEndTerm = memberState.currTerm();
            legerEndIndex = entry.getIndex();
            committedIndex = entry.getIndex();
            cachedEntries.put(entry.getIndex(), entry);
            if (legerBeginIndex == -1) {
                legerBeginIndex = legerEndIndex;
            }
            return entry.getIndex();
        }

    }

    @Override
    public DLegerEntry get(Long index) {
        return cachedEntries.get(index);
    }


    public long getLegerEndIndex() {
        return legerEndIndex;
    }

    @Override public long getLegerBeginIndex() {
        return legerBeginIndex;
    }

    public long getCommittedIndex() {
        return committedIndex;
    }

    public long getLegerEndTerm() {
        return legerEndTerm;
    }
}
