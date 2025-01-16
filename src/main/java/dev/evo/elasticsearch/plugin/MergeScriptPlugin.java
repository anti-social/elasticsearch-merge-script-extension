package dev.evo.elasticsearch.plugin;

import dev.evo.elasticsearch.MergeScriptExtBuilder;
import dev.evo.elasticsearch.MergeScriptFilter;
import dev.evo.elasticsearch.script.MergeScript;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.NodeEnvironment;
import org.elasticsearch.painless.spi.Whitelist;
import org.elasticsearch.painless.spi.WhitelistLoader;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.plugins.SearchPlugin;
import org.elasticsearch.repositories.RepositoriesService;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.watcher.ResourceWatcherService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class MergeScriptPlugin extends Plugin
    implements ActionPlugin, SearchPlugin, ScriptPlugin
{
    private final Settings settings;
    private ScriptService scriptService;

    public MergeScriptPlugin(Settings settings) {
        this.settings = settings;
    }

    @Override
    public Collection<Object> createComponents(
            Client client,
            ClusterService clusterService,
            ThreadPool threadPool,
            ResourceWatcherService resourceWatcherService,
            ScriptService scriptService,
            NamedXContentRegistry xContentRegistry,
            Environment environment,
            NodeEnvironment nodeEnvironment,
            NamedWriteableRegistry namedWriteableRegistry,
            IndexNameExpressionResolver indexNameExpressionResolver,
            Supplier<RepositoriesService> repositoriesServiceSupplier) {
        this.scriptService = scriptService;
        return Collections.emptyList();
    }

    @Override
    public List<ScriptContext<?>> getContexts() {
        return Collections.singletonList(MergeScript.CONTEXT);
    }

    @Override
    public List<ActionFilter> getActionFilters() {
        return Collections.singletonList(
            new MergeScriptFilter(settings, scriptService)
        );
    }

    @Override
    public List<SearchExtSpec<?>> getSearchExts() {
        return Collections.singletonList(
            new SearchExtSpec<>(
                MergeScriptExtBuilder.NAME,
                MergeScriptExtBuilder::new,
                MergeScriptExtBuilder::fromXContent
            )
        );
    }
}
