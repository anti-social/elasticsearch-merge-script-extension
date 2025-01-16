package dev.evo.elasticsearch;

import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchExtBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class MergeScriptExtBuilder extends SearchExtBuilder {
    public static final String NAME = "merge_script";

    // Window size on which we will operate to group and rescore documents
    private static final ParseField WINDOW_SIZE_FIELD_NAME = new ParseField("window_size");
    private static final int DEFAULT_WINDOW_SIZE = 10_000;

    // Number of documents that will be returned from a shard
    private static final ParseField PAGINATION_FIELD_NAME = new ParseField("pagination");
    private static final boolean DEFAULT_PAGINATION = true;

    private static final ParseField MERGE_SCRIPT_FIELD = new ParseField("merge_script");

    private static final ConstructingObjectParser<MergeScriptExtBuilder, Void> PARSER =
        new ConstructingObjectParser<>(
            NAME,
            args -> new MergeScriptExtBuilder((MergeScript) args[0])
        );
    static {
        PARSER.declareObject(
            ConstructingObjectParser.constructorArg(), MergeScript.PARSER, MERGE_SCRIPT_FIELD
        );
        PARSER.declareInt(MergeScriptExtBuilder::windowSize, WINDOW_SIZE_FIELD_NAME);
        PARSER.declareBoolean(MergeScriptExtBuilder::pagination, PAGINATION_FIELD_NAME);
    }

    private final MergeScript mergeScript;
    private int windowSize = DEFAULT_WINDOW_SIZE;
    private boolean pagination = DEFAULT_PAGINATION;

    public static class MergeScript implements Writeable {
        public final Script script;
        public final List<String> fields;

        private static final ParseField SCRIPT_FIELD = new ParseField("script");
        private static final ParseField FIELDS_FIELD = new ParseField("fields");
        @SuppressWarnings("unchecked")
        private static final ConstructingObjectParser<MergeScript, Void> PARSER =
            new ConstructingObjectParser<>(
                SCRIPT_FIELD.getPreferredName(),
                args -> new MergeScript((Script) args[0], (List<String>) args[1])
            );
        static {
            PARSER.declareObject(
                ConstructingObjectParser.constructorArg(),
                (p, c) -> Script.parse(p),
                SCRIPT_FIELD
            );
            PARSER.declareStringArray(
                ConstructingObjectParser.optionalConstructorArg(),
                FIELDS_FIELD
            );
        }

        public MergeScript(Script script) {
            this(script, List.of());
        }

        public MergeScript(Script script, List<String> fields) {
            this.script = script;
            this.fields = fields;
        }

        public MergeScript(StreamInput in) throws IOException {
            script = new Script(in);
            fields = in.readOptionalStringList();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            script.writeTo(out);
            out.writeOptionalStringCollection(fields);
        }
    }

    public MergeScriptExtBuilder(MergeScript mergeScript) {
        this.mergeScript = mergeScript;
    }

    public MergeScriptExtBuilder(StreamInput in) throws IOException {
        windowSize = in.readInt();
        pagination = in.readBoolean();
        mergeScript = new MergeScript(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeInt(windowSize);
        out.writeBoolean(pagination);
        mergeScript.writeTo(out);
    }

    public static MergeScriptExtBuilder fromXContent(XContentParser parser) {
        return PARSER.apply(parser, null);
    }

    public MergeScriptExtBuilder windowSize(int size) {
        this.windowSize = size;
        return this;
    }

    public int windowSize() {
        return windowSize;
    }

    public MergeScriptExtBuilder pagination(boolean pagination) {
        this.pagination = pagination;
        return this;
    }

    public boolean pagination() {
        return pagination;
    }

    public MergeScript mergeScript() {
        return mergeScript;
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        builder.field(WINDOW_SIZE_FIELD_NAME.getPreferredName(), windowSize);
        builder.field(PAGINATION_FIELD_NAME.getPreferredName(), pagination);
        builder.field(MERGE_SCRIPT_FIELD.getPreferredName(), mergeScript);
        builder.endObject();
        return builder;
    }

    @Override
    public int hashCode() {
        return Objects.hash(windowSize, pagination, mergeScript);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MergeScriptExtBuilder)) {
            return false;
        }
        var other = (MergeScriptExtBuilder) obj;
        return other.windowSize == windowSize &&
            other.pagination == pagination &&
            other.mergeScript.equals(mergeScript);
    }
}
