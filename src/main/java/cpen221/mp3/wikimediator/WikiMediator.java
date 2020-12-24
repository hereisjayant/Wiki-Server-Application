package cpen221.mp3.wikimediator;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import cpen221.mp3.fsftbuffer.Bufferable;
import cpen221.mp3.fsftbuffer.FSFTBuffer;
import cpen221.mp3.query.QueryFactory;
import org.fastily.jwiki.core.Wiki;
import org.fastily.jwiki.dwrap.ProtectedTitleEntry;

import java.io.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class WikiMediator {

    /*
        Abstraction Function:
            pageText represents the the text of the Wikipedia page

            pageTitle represents the title of the Wikipedia page

            pageCache caches the items of a Wikipedia page

            searchCache finds the cache items from the Wikipedia page

            log represents the queries called in a certain time period

            peakLoadLog represents the peak number of requests in a time period

            Representation Invariant:
                pageText and pageTitle are non-null

                pageCache and searchCache are non-null with capacity of requests < 100.
                The timeout for requets is defined to be 0 < timeout < 1000.
                Capacity and timeout are positive integers.

                log and peakLoadLog are positive long values
         */

    private class PageCacheItem implements Bufferable {
        String pageText;
        String pageTitle;

        public PageCacheItem (String pageText, String pageTitle) {
            this.pageText = pageText;
            this.pageTitle = pageTitle;
        }

        @Override
        public String id() {
            return pageTitle;
        }
    }

    private class SearchCacheItem implements Bufferable {
        List<String> pageList;
        String query;
        int limit;

        public SearchCacheItem (List<String> pageList, String query, int limit) {
            this.pageList = pageList;
            this.query = query;
            this.limit = limit;
        }

        @Override
        public String id() {
            return query;
        }

        public int getLimit() {
            return limit;
        }
    }

    private Wiki wiki;
    private FSFTBuffer<PageCacheItem> pageCache;
    private FSFTBuffer<SearchCacheItem> searchCache;
    private Map<Timestamp, String> log;
    private List<Timestamp> peakLoadLog;
    private final String DEFAULT_FILENAME_LOG = "local/logs.txt";
    private final String DEFAULT_FILENAME_PEAKLOAD = "local/logs_peak.txt";


    public WikiMediator() {
        wiki = new Wiki.Builder().build();
        pageCache = new FSFTBuffer<>(100, 1000);
        searchCache = new FSFTBuffer<>(100, 1000);
        log = new ConcurrentHashMap<>();
        peakLoadLog = Collections.synchronizedList(new LinkedList<>());
    }

    public WikiMediator(File log_file, File peakload_file) throws FileNotFoundException {
        wiki = new Wiki.Builder().build();
        pageCache = new FSFTBuffer<>(100, 1000);
        searchCache = new FSFTBuffer<>(100, 1000);
        Gson gson = new Gson();
        log = Collections.synchronizedMap(gson.fromJson(new FileReader(log_file), new TypeToken<HashMap<Timestamp, String>>(){}.getType()));
        peakLoadLog = Collections.synchronizedList(gson.fromJson(new FileReader(peakload_file), new TypeToken<List<Timestamp>>(){}.getType()));
    }

    public void saveLogs(File log_file, File peakload_file) {
        Gson gson = new Gson();
        try (FileWriter writer = new FileWriter(log_file)) {
            gson.toJson(log, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (FileWriter writer = new FileWriter(peakload_file)) {
            gson.toJson(peakLoadLog, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Compares the given query with the Wikipedia query
     * @param query
     * @param limit
     * @return a list of page titles that matches the query
     */
    public synchronized List<String> search(String query, int limit){
        log.put(new Timestamp(System.currentTimeMillis()), query);
        peakLoadLog.add(new Timestamp(System.currentTimeMillis()));

        try {
            SearchCacheItem searchCacheItem = searchCache.get(query);

            if (searchCacheItem.getLimit() >= limit) {
                List<String> queryResults = new ArrayList<>();
                for (int i = 0; i < limit; i++) {
                    queryResults.add(searchCacheItem.pageList.get(i));
                }
                return queryResults;
            }
            else {
                ArrayList<String> pageTitles = new ArrayList<>(wiki.search(query,limit));
                searchCache.update(new SearchCacheItem(pageTitles, query, limit));
                return pageTitles;
            }
        }
        catch (IllegalAccessException e) {
            ArrayList<String> pageTitles = new ArrayList<>(wiki.search(query,limit));
            searchCache.put(new SearchCacheItem(pageTitles, query, limit));
            return pageTitles;
        }
    }

    /**
     * Compares the text of pageTitle with the Wikipedia text
     * @param pageTitle
     * @return text that matches pageTitle
     */
    public synchronized String getPage(String pageTitle){
        log.put(new Timestamp(System.currentTimeMillis()), pageTitle);
        peakLoadLog.add(new Timestamp(System.currentTimeMillis()));

        try {
            PageCacheItem pageCacheItem = pageCache.get(pageTitle);
            return pageCacheItem.pageText;
        } catch (IllegalAccessException e) {
            String pageText = wiki.getPageText(pageTitle);
            pageCache.put(new PageCacheItem(pageText, pageTitle));
            return pageText;
        }
    }

    /**
     * Calculates the most common string in non-increasing order
     * @param limit limit to the length of returned list
     * @return the most common string
     */
    public synchronized List<String> zeitgeist(int limit){
        peakLoadLog.add(new Timestamp(System.currentTimeMillis()));

        List<String> loggedStrings = new LinkedList<>(log.values());
        Map<String, Integer> stringCounts = new HashMap<>();

        for (String string : loggedStrings) {
            if (!stringCounts.containsKey(string)) {
                stringCounts.put(string, 0);
            }
            int prevCount = stringCounts.get(string);
            stringCounts.put(string, prevCount + 1);
        }

        List<String> stringList = new LinkedList<>(stringCounts.keySet());
        Collections.sort(stringList, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                if (stringCounts.get(o1) > stringCounts.get(o2)) {
                    return -1;
                }
                else if (stringCounts.get(o1) < stringCounts.get(o2)) {
                    return 1;
                }
                return 0;
            }
        });

        if (stringList.size() <= limit) {
            return stringList;
        }
        else {
            List<String> limitedStringList = new LinkedList<>();
            for (int i = 0; i < limit; i++) {
                limitedStringList.add(stringList.get(i));
            }
            return limitedStringList;
        }

    }

    /**
     * Returns a list of Strings ranked by the number of most frequently made requests
     * @param limit limit to the length of returned list
     * @return list of Strings ranked by most frequent requests
     */
    public List<String> trending(int limit){
        peakLoadLog.add(new Timestamp(System.currentTimeMillis()));

        Long currentMillis = System.currentTimeMillis();
        Set<Timestamp> past30sRequestedTimes = new HashSet<>();
        for (Timestamp timestamp : log.keySet()) {
            if (currentMillis - timestamp.getTime() <= 30 * 1000) {
                past30sRequestedTimes.add(timestamp);
            }
        }

        List<Timestamp> past30sReqeuestedTimesList = new LinkedList<>(past30sRequestedTimes);
        Collections.sort(past30sReqeuestedTimesList, new Comparator<Timestamp>() {
            @Override
            public int compare(Timestamp o1, Timestamp o2) {
                if (o1.getTime() > o2.getTime()) {
                    return -1;
                }
                if (o1.getTime() < o2.getTime()) {
                    return 1;
                }
                return 0;
            }
        });

        Set<String> temporaryStringSet = new HashSet<>();
        List<String> mostFrequentStringList = new LinkedList<>();
        for (Timestamp timestamp : past30sReqeuestedTimesList) {
            if (!temporaryStringSet.contains(log.get(timestamp))) {
                temporaryStringSet.add(log.get(timestamp));
                mostFrequentStringList.add(log.get(timestamp));
            }
        }

        if (mostFrequentStringList.size() <= limit) {
            return mostFrequentStringList;
        }
        else {
            List<String> limitedStringList = new LinkedList<>();
            for (int i = 0; i < limit; i++) {
                limitedStringList.add(mostFrequentStringList.get(i));
            }
            return limitedStringList;
        }

    }

    /**
     *  Calculates the number of request count
     * @return request count
     */
    public int peakLoad30s() {
        peakLoadLog.add(new Timestamp(System.currentTimeMillis()));




        return -1;
    }

    public List<String> executeQuery(String query) {
        return QueryFactory.parse(query);
    }
}