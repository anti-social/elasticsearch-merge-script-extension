package dev.evo.elasticsearch.rescore;

import dev.evo.elasticsearch.MergeScriptExtBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.test.ESIntegTestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.elasticsearch.cluster.metadata.IndexMetadata.SETTING_NUMBER_OF_SHARDS;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertOrderedSearchHits;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertRequestBuilderThrows;

@ESIntegTestCase.ClusterScope(scope = ESIntegTestCase.Scope.SUITE)
public class MergeScriptExtFilterIT extends ESIntegTestCase {
    private final static int NUMBER_OF_SHARDS = 2;

    public void testEmpty() throws IOException {
        createIndex(NUMBER_OF_SHARDS);

        var resp = rescoreSearchRequest().get();
        assertHitCount(resp, 0);
    }

    public void testMergeScript() throws IOException {
        createIndex(NUMBER_OF_SHARDS);
        populateIndex();

        var resp = rescoreSearchRequest().get();
        assertHitCount(resp, 6);
        assertOrderedSearchHits(resp, "101", "201", "103", "304", "301", "204");
    }

    public void testMergeScriptWithPagination() throws IOException {
        createIndex(NUMBER_OF_SHARDS);
        populateIndex();

        var resp = rescoreSearchRequest(10_000, true, 2, 3).get();
        assertHitCount(resp, 6);
        assertOrderedSearchHits(resp, "103", "304", "301");
    }

    public void testMergeScriptWithPaginationOverflow() throws IOException {
        createIndex(NUMBER_OF_SHARDS);
        populateIndex();

        var resp = rescoreSearchRequest(10_000, true, 7, 3).get();
        assertHitCount(resp, 6);
        assertOrderedSearchHits(resp);
    }

    public void testMergeScriptWithoutPagination() throws IOException {
        createIndex(NUMBER_OF_SHARDS);
        populateIndex();

        var resp = rescoreSearchRequest(10_000, false, 2, 3).get();
        assertHitCount(resp, 6);
        assertOrderedSearchHits(resp, "101", "201", "103", "304", "301", "204");
    }

    private SearchRequestBuilder rescoreSearchRequest() {
        return rescoreSearchRequest(10_000, true, 0, 10);
    }

    private SearchRequestBuilder rescoreSearchRequest(
        int windowSize, boolean pagination, int from, int size
    ) {
        return rescoreSearchRequest(
            windowSize,
            pagination,
            from,
            size,
            new MergeScriptExtBuilder.MergeScript(
                new Script(
                    ScriptType.INLINE,
                    "painless",
                    """
                        SearchHit[] mixedHits = new SearchHit[hits.length];
                        List commonHits = new ArrayList();
                        List turboHits = new ArrayList();
                        for (SearchHit hit : hits) {
                            def isTurbo = (Boolean) hit.field("is_turbo").value;
                            if (isTurbo != null && isTurbo) {
                                turboHits.add(hit);
                            } else {
                                commonHits.add(hit);
                            }
                        }
                        List hitsIterators = new ArrayList();
                        hitsIterators.add(commonHits.iterator());
                        hitsIterators.add(turboHits.iterator());
                        int i = 0;
                        while (!hitsIterators.isEmpty()) {
                            ListIterator hitsIteratorsIter = hitsIterators.listIterator();
                            while (hitsIteratorsIter.hasNext()) {
                                Iterator hitsIter = hitsIteratorsIter.next();
                                if (hitsIter.hasNext()) {
                                    mixedHits[i++] = hitsIter.next();
                                } else {
                                    hitsIteratorsIter.remove();
                                }

                            }
                        }
                        return mixedHits;
                    """,
                    Collections.emptyMap()
                ),
                List.of("is_turbo")
            )
        );
    }

    private SearchRequestBuilder rescoreSearchRequest(
        int windowSize,
        boolean pagination,
        int from,
        int size,
        MergeScriptExtBuilder.MergeScript mergeScript
    ) {
        return client().prepareSearch()
            .setSource(
                SearchSourceBuilder.searchSource()
                    .from(from)
                    .size(size)
                    .query(
                        QueryBuilders.functionScoreQuery(
                            ScoreFunctionBuilders.scriptFunction(
                                new Script(
                                    "return doc['rank'].size() > 0 ? doc['rank'].value : 0.0;"
                                )
                            )
                        )
                    )
                    .ext(
                        List.of(
                            new MergeScriptExtBuilder(mergeScript)
                                .windowSize(windowSize)
                                .pagination(pagination)
                        )
                    )
            );
    }

    private void createIndex(int numShards) throws IOException {
        assertAcked(
            prepareCreate("test")
                .setSettings(Settings.builder().put(SETTING_NUMBER_OF_SHARDS, numShards))
                .addMapping("product",
                    jsonBuilder()
                        .startObject()
                          .startObject("product")
                            .startObject("properties")
                              .startObject("name")
                                .field("type", "text")
                                .field("analyzer", "whitespace")
                              .endObject()
                              .startObject("company_id")
                                .field("type", "integer")
                              .endObject()
                              .startObject("rank")
                                .field("type", "float")
                              .endObject()
                              .startObject("is_turbo")
                                .field("type", "boolean")
                              .endObject()
                            .endObject()
                          .endObject()
                        .endObject())
        );
        ensureYellow();
    }

    private void populateIndex() throws IOException {
        client().prepareIndex("test", "product", "101")
            .setSource(
                "name", "the quick brown fox",
                "company_id", 1,
                "rank", 2.1,
                "is_turbo", false
            )
            .get();
        client().prepareIndex("test", "product", "103")
            .setSource(
                "name", "quick huge brown fox",
                "company_id", 1,
                "rank", 1.9,
                "is_turbo", false
            )
            .get();
        refresh();
        // Ensure products are placed on different shards
        assertHitCount(
            client().prepareSearch()
                .setQuery(
                    QueryBuilders.boolQuery()
                        .filter(QueryBuilders.termQuery("company_id", 1))
                )
                .setPreference("_shards:0")
                .get(),
        1
        );

        client().prepareIndex("test", "product", "201")
            .setSource(
                "name", "the quick lazy huge fox jumps over the tree",
                "company_id", 2,
                "rank", 1.6,
                "is_turbo", true
            )
            .get();
        client().prepareIndex("test", "product", "204")
            .setSource(
                "name", "the brother of the quick lazy fox",
                "company_id", 2,
                "is_turbo", true
            )
            .get();
        refresh();
        // Ensure products are placed on different shards
        assertHitCount(
            client().prepareSearch()
                .setQuery(
                    QueryBuilders.boolQuery()
                        .filter(QueryBuilders.termQuery("company_id", 2))
                )
                .setPreference("_shards:0")
                .get(),
            1
        );

        client().prepareIndex("test", "product", "301")
            .setSource(
                "name", "the quick lonely fox",
                "rank", 1.0
            )
            .get();
        client().prepareIndex("test", "product", "304")
            .setSource(
                "name", "another lonely fox",
                "rank", 0.8,
                "is_turbo", true
            )
            .get();
        refresh();
        // Ensure products are placed on different shards
        assertHitCount(
            client().prepareSearch()
                .setQuery(
                    QueryBuilders.boolQuery()
                        .filter(
                            QueryBuilders.boolQuery()
                                .mustNot(QueryBuilders.existsQuery("company_id"))
                        )
                )
                .setPreference("_shards:0")
                .get(),
            1
        );

        ensureYellow();
    }
}
