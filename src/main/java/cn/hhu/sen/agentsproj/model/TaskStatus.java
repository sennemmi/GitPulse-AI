package cn.hhu.sen.agentsproj.model;

import java.util.Map;
import java.util.Set;

public enum TaskStatus {
    PENDING,
    INTENT_RECOGNIZED,
    DATA_FETCHING,
    AI_ANALYZING,
    REPORT_GENERATING,
    SUCCESS,
    FAILED;

    private static final Map<TaskStatus, Set<TaskStatus>> TRANSITIONS = Map.of(
            PENDING,           Set.of(INTENT_RECOGNIZED, FAILED),
            INTENT_RECOGNIZED, Set.of(DATA_FETCHING, FAILED),
            DATA_FETCHING,     Set.of(AI_ANALYZING, FAILED),
            AI_ANALYZING,      Set.of(REPORT_GENERATING, FAILED),
            REPORT_GENERATING, Set.of(SUCCESS, FAILED),
            SUCCESS,           Set.of(),
            FAILED,            Set.of(PENDING)
    );

    public TaskStatus transition(TaskStatus next) {
        if (!TRANSITIONS.get(this).contains(next)) {
            throw new IllegalStateException(
                    "非法状态转换: " + this + " -> " + next);
        }
        return next;
    }
}
