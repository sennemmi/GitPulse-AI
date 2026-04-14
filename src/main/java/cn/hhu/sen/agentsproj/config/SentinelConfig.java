package cn.hhu.sen.agentsproj.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SentinelConfig {

    @PostConstruct
    public void initRules() {
        initFlowRules();
        initDegradeRules();
    }

    private void initFlowRules() {
        FlowRule flowRule = new FlowRule();
        flowRule.setResource("analyzeRepo");
        flowRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        flowRule.setCount(10);
        FlowRuleManager.loadRules(List.of(flowRule));
    }

    private void initDegradeRules() {
        DegradeRule degradeRule = new DegradeRule();
        degradeRule.setResource("llmCall");
        degradeRule.setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType());
        degradeRule.setCount(0.5);
        degradeRule.setStatIntervalMs(10000);
        degradeRule.setTimeWindow(10);
        degradeRule.setMinRequestAmount(5);
        DegradeRuleManager.loadRules(List.of(degradeRule));
    }
}
