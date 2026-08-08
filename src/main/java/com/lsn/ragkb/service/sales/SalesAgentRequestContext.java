package com.lsn.ragkb.service.sales;

import com.lsn.ragkb.dto.RagResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SalesAgentRequestContext {

    private final ThreadLocal<State> state = new InheritableThreadLocal<>() {
        @Override
        protected State initialValue() {
            return new State();
        }

        @Override
        protected State childValue(State parentValue) {
            return parentValue.copy();
        }
    };

    public void init(List<Long> kbIds) {
        State current = new State();
        current.kbIds = kbIds == null ? List.of() : List.copyOf(kbIds);
        state.set(current);
    }

    public List<Long> kbIds() {
        return state.get().kbIds;
    }

    public void setKnowledgeAnswer(String answer) {
        state.get().knowledgeAnswer = answer == null ? "" : answer;
    }

    public String knowledgeAnswer() {
        return state.get().knowledgeAnswer;
    }

    public void setSources(List<RagResponse.Source> sources) {
        state.get().sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public List<RagResponse.Source> sources() {
        return state.get().sources;
    }

    public void addToolTrace(String trace) {
        if (trace != null && !trace.isBlank()) {
            state.get().toolTraces.add(trace);
        }
    }

    public List<String> toolTraces() {
        return List.copyOf(state.get().toolTraces);
    }

    public void clear() {
        state.remove();
    }

    private static class State {
        private List<Long> kbIds = List.of();
        private String knowledgeAnswer = "";
        private List<RagResponse.Source> sources = List.of();
        private final List<String> toolTraces = new ArrayList<>();

        private State copy() {
            State copy = new State();
            copy.kbIds = this.kbIds;
            copy.knowledgeAnswer = this.knowledgeAnswer;
            copy.sources = this.sources;
            copy.toolTraces.addAll(this.toolTraces);
            return copy;
        }
    }
}
