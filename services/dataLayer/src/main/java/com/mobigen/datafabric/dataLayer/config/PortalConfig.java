package com.mobigen.datafabric.dataLayer.config;

import lombok.Getter;
import org.apache.commons.configuration.Configuration;

import java.util.List;

@Getter
public class PortalConfig {
    private final Configuration config;
    private final String dataModelIndex;
    private final String storageIndex;
    private final String recentSearchesIndex;
    private final String host;
    private final int port;
    private final int numberOfSaveRecentSearches;
    private final int numberOfRecentSearches;
    private final List<String> filters;
    private final int limitSearchSize;
    private final int defaultSearchSize;

    public PortalConfig(Configuration config) {
        this.config = config;
        this.dataModelIndex = config.getString("open_search.index.data_model").toLowerCase();
        this.storageIndex = config.getString("open_search.index.storage").toLowerCase();
        this.recentSearchesIndex = config.getString("open_search.index.recent_searches").toLowerCase();
        this.host = config.getString("open_search.host");
        this.port = config.getInt("open_search.port");
        this.numberOfSaveRecentSearches = config.getInt("open_search.number_of_save_recent_searches");
        this.numberOfRecentSearches = config.getInt("open_search.number_of_recent_searches");
        this.filters = config.getList("open_search.filters");
        this.limitSearchSize = config.getInt("open_search.limit_search_size");
        this.defaultSearchSize = config.getInt("open_search.default_search_size");
    }
}
