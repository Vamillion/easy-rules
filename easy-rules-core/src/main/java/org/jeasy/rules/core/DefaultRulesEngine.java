/*
 * The MIT License
 *
 *  Copyright (c) 2020, Mahmoud Ben Hassine (mahmoud.benhassine@icloud.com)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package org.jeasy.rules.core;

import org.jeasy.rules.api.Fact;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rule;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngine;
import org.jeasy.rules.api.RulesEngineParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Default {@link RulesEngine} implementation.
 *
 * Rules are fired according to their natural order which is priority by default.
 * This implementation iterates over the sorted set of rules, evaluates the condition
 * of each rule and executes its actions if the condition evaluates to true.
 *
 * @author Mahmoud Ben Hassine (mahmoud.benhassine@icloud.com)
 */
public final class DefaultRulesEngine extends AbstractRulesEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRulesEngine.class);

    /**
     * Create a new {@link DefaultRulesEngine} with default parameters.
     */
    public DefaultRulesEngine() {
        super();
    }

    /**
     * Create a new {@link DefaultRulesEngine}.
     *
     * @param parameters of the engine
     */
    public DefaultRulesEngine(final RulesEngineParameters parameters) {
        super(parameters);
    }

    @Override
    public void fire(Rules rules, Facts facts) {
        Objects.requireNonNull(rules, "Rules are not allowed to be null.");
        Objects.requireNonNull(facts, "Facts are not allowed to be null.");
        triggerListenersBeforeRules(rules, facts);
        doFire(rules, facts);
        triggerListenersAfterRules(rules, facts);
    }

    void doFire(Rules rules, Facts facts) {
        if (rules.isEmpty()) {
            LOGGER.warn("No rules registered! Nothing to apply");
            return;
        }
        logAll(rules, facts);
        LOGGER.trace("Rules evaluation started");
        for (Rule rule : rules) {
            final String name = rule.getName();
            final int priority = rule.getPriority();
            if (priority > parameters.getPriorityThreshold()) {
                LOGGER.trace("Rule priority threshold ({}) exceeded at rule '{}' with priority={}, next rules will be skipped",
                        parameters.getPriorityThreshold(), name, priority);
                break;
            }
            if (!shouldBeEvaluated(rule, facts)) {
                LOGGER.trace("Rule '{}' has been skipped before being evaluated", name);
                continue;
            }
            boolean evaluationResult = false;
            try {
                evaluationResult = rule.evaluate(facts);
            } catch (RuntimeException exception) {
                LOGGER.error("Rule '{}' evaluated with error", name, exception);
                triggerListenersOnEvaluationError(rule, facts, exception);
                // give the option to either skip next rules on evaluation error or continue by considering the evaluation error as false
                if (parameters.isSkipOnFirstNonTriggeredRule()) {
                    LOGGER.trace("Next rules will be skipped since parameter skipOnFirstNonTriggeredRule is set");
                    break;
                }
            }
            if (evaluationResult) {
                LOGGER.trace("Rule '{}' triggered", name);
                triggerListenersAfterEvaluate(rule, facts, true);
                try {
                    triggerListenersBeforeExecute(rule, facts);
                    rule.execute(facts);
                    LOGGER.trace("Rule '{}' performed successfully", name);
                    triggerListenersOnSuccess(rule, facts);
                    if (parameters.isSkipOnFirstAppliedRule()) {
                        LOGGER.trace("Next rules will be skipped since parameter skipOnFirstAppliedRule is set");
                        break;
                    }
                } catch (Exception exception) {
                    LOGGER.error("Rule '{}' performed with error", name, exception);
                    triggerListenersOnFailure(rule, exception, facts);
                    if (parameters.isSkipOnFirstFailedRule()) {
                        LOGGER.trace("Next rules will be skipped since parameter skipOnFirstFailedRule is set");
                        break;
                    }
                }
            } else {
                LOGGER.trace("Rule '{}' has been evaluated to false, it has not been executed", name);
                triggerListenersAfterEvaluate(rule, facts, false);
                if (parameters.isSkipOnFirstNonTriggeredRule()) {
                    LOGGER.trace("Next rules will be skipped since parameter skipOnFirstNonTriggeredRule is set");
                    break;
                }
            }
        }
    }

    /**
     * @deprecated In case of accepting PR, a remove of this method need to be done
     */
    @Deprecated
    private void logEngineParameters() {
        LOGGER.trace("{}", parameters);
    }

    /**
     * Method using all known information to construct a single log entry on Level trace.
     * <p>
     * This method will iterate over the provided {@link Rules} and {@link Facts} building a full log message with
     * the provided {@link RulesEngineParameters}. The message is constructed using {@link StringBuilder}.</p>
     *
     * @param rules Provided {@link Rules} to handle by the engine
     * @param facts Provided {@link Facts}
     */
    void logAll(Rules rules, Facts facts) {
        String logMessage  = String.format("%nEngine parameters: %n%s%n", parameters);
        StringBuilder rulesMessage = new StringBuilder("Registered rules:");
        StringBuilder factsMessage = new StringBuilder("Known facts:");

        for (Rule rule : rules) {
            rulesMessage.append(String.format("%nRule { name = '%s', description = '%s', priority = '%s'}%n",
                    rule.getName(), rule.getDescription(), rule.getPriority()));
        }

        for (Fact<?> fact : facts) {
            factsMessage.append(String.format("%n%s", fact));
        }

        LOGGER.trace("{}{}{}", logMessage, rulesMessage, factsMessage);
    }
    /**
     * @deprecated In case of accepting PR, a remove of this method need to be done
     */
    @Deprecated
    private void log(Rules rules) {
        LOGGER.trace("Registered rules:");
        for (Rule rule : rules) {
            LOGGER.trace("Rule { name = '{}', description = '{}', priority = '{}'}",
                    rule.getName(), rule.getDescription(), rule.getPriority());
        }
    }
    /**
     * @deprecated In case of accepting PR, a remove of this method need to be done
     */
    @Deprecated
    private void log(Facts facts) {
        LOGGER.trace("Known facts:");
        for (Fact<?> fact : facts) {
            LOGGER.trace("{}", fact);
        }
    }

    @Override
    public Map<Rule, Boolean> check(Rules rules, Facts facts) {
        Objects.requireNonNull(rules, "Rules are not allowed to be null.");
        Objects.requireNonNull(facts, "Facts are not allowed to be null.");
        triggerListenersBeforeRules(rules, facts);
        Map<Rule, Boolean> result = doCheck(rules, facts);
        triggerListenersAfterRules(rules, facts);
        return result;
    }

    private Map<Rule, Boolean> doCheck(Rules rules, Facts facts) {
        LOGGER.trace("Checking rules");
        Map<Rule, Boolean> result = new HashMap<>();
        for (Rule rule : rules) {
            if (shouldBeEvaluated(rule, facts)) {
                result.put(rule, rule.evaluate(facts));
            }
        }
        return result;
    }

    private void triggerListenersOnFailure(final Rule rule, final Exception exception, Facts facts) {
        ruleListeners.forEach(ruleListener -> ruleListener.onFailure(rule, facts, exception));
    }

    private void triggerListenersOnSuccess(final Rule rule, Facts facts) {
        ruleListeners.forEach(ruleListener -> ruleListener.onSuccess(rule, facts));
    }

    private void triggerListenersBeforeExecute(final Rule rule, Facts facts) {
        ruleListeners.forEach(ruleListener -> ruleListener.beforeExecute(rule, facts));
    }

    private boolean triggerListenersBeforeEvaluate(Rule rule, Facts facts) {
        return ruleListeners.stream().allMatch(ruleListener -> ruleListener.beforeEvaluate(rule, facts));
    }

    private void triggerListenersAfterEvaluate(Rule rule, Facts facts, boolean evaluationResult) {
        ruleListeners.forEach(ruleListener -> ruleListener.afterEvaluate(rule, facts, evaluationResult));
    }

    private void triggerListenersOnEvaluationError(Rule rule, Facts facts, Exception exception) {
        ruleListeners.forEach(ruleListener -> ruleListener.onEvaluationError(rule, facts, exception));
    }

    private void triggerListenersBeforeRules(Rules rule, Facts facts) {
        rulesEngineListeners.forEach(rulesEngineListener -> rulesEngineListener.beforeEvaluate(rule, facts));
    }

    private void triggerListenersAfterRules(Rules rule, Facts facts) {
        rulesEngineListeners.forEach(rulesEngineListener -> rulesEngineListener.afterExecute(rule, facts));
    }

    private boolean shouldBeEvaluated(Rule rule, Facts facts) {
        return triggerListenersBeforeEvaluate(rule, facts);
    }

}
