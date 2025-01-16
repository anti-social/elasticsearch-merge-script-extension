package dev.evo.elasticsearch.script;

import org.elasticsearch.index.fielddata.ScriptDocValues;
import org.elasticsearch.script.DynamicMap;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptFactory;
import org.elasticsearch.search.SearchHit;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class MergeScript {
    public static final String[] PARAMETERS = {"hits"};

    public interface Factory extends ScriptFactory {
        MergeScript newInstance(Map<String, Object> params);
    }

    public static final ScriptContext<Factory> CONTEXT = new ScriptContext<>("merge", Factory.class);

    private final Map<String, Object> params;

    public MergeScript(Map<String, Object> params) {
        this.params = params;
    }

    /** Provides access to script parameters */
    public Map<String, Object> getParams() {
        return params;
    }

    public abstract SearchHit[] execute(SearchHit[] hits);
}
