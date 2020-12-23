package cpen221.mp3.wikimediator;

import cpen221.mp3.query.QueryFactory;
import org.fastily.jwiki.core.Wiki;
import org.fastily.jwiki.dwrap.ProtectedTitleEntry;

import java.util.*;


public class WikiMediator {

    private Wiki wiki = null;
    private HashMap<String, Integer> M1 = new HashMap<String, Integer>(), M2 = new HashMap<String,Integer>(), F1 = new HashMap<String,Integer>();
    private HashMap<Long,String> F2 = new HashMap<Long,String >();
    private HashMap<Long,Integer> timeSlots = new HashMap<Long, Integer>();
    private int maximumRequestSlot = 0;

    public WikiMediator() {
        wiki = new Wiki.Builder().build();
    }

    /**
     * Compares the given query with the Wikipedia query
     * @param query
     * @param limit
     * @return a list of page titles that matches the query
     */
    public List<String> search(String query, int limit){
        updateTimeWiseTrack(query);
        calculateMaximumRequestSlot();
        int value = 0;
        if(M1.containsKey(query)){
            value = M1.get(query);
        }
        value = value + 1;
        M1.put(query,value);
        return wiki.search(query, limit);
    }

    /**
     * Compares the text of pageTitle with the Wikipedia text
     * @param pageTitle
     * @return text that matches pageTitle
     */
    public String getPage(String pageTitle){
        updateTimeWiseTrack(pageTitle);
        calculateMaximumRequestSlot();
        int value = 0;
        if(M2.containsKey(pageTitle)){
            value = M2.get(pageTitle);
        }
        value = value + 1;
        M2.put(pageTitle,value);
        return wiki.getPageText(pageTitle);
    }

    /**
     * Calculates the most common string in non-increasing order
     * @param limit
     * @return the most common string
     */
    public List<String> zeitgeist(int limit){
        calculateMaximumRequestSlot();
        List<String> result = new ArrayList<String>();
        Map<Integer, List<String>> map = new LinkedHashMap<Integer, List<String>>();
        List<String> values;
        for (Map.Entry<String, Integer> entry : M1.entrySet()) {
            String key = entry.getKey();
            int value = entry.getValue();
            if(!M2.containsKey(key)){
                continue;
            }
            int value2 = M2.get(key);
            if(value2 < value){
                value = value2;
            }
            if(value <= 0){
                continue;
            }
            value = -value;

            if(map.containsKey(value)){
                values = map.get(value);
            }
            else {
                values = new ArrayList<String>();
            }
            values.add(key);
        }

        for (Map.Entry<Integer, List<String>> entry : map.entrySet()) {
            for (String val : entry.getValue()) {
                if (result.size() == limit) {
                    return result;
                }
                result.add(val);
            }
        }
        return result;
    }

    /**
     * Calculates the number of most frequently made requests
     * @param limit
     * @return the most frequent requests
     */
    public List<String> trending(int limit){
        calculateMaximumRequestSlot();
        List<String> result = new ArrayList<String>();
        Map<Integer, List<String>> map = new LinkedHashMap<Integer, List<String>>();
        List<String> values;
        long lasttime = (System.currentTimeMillis() / 1000) - 30;

        Iterator it = F2.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            long curTime = (long)pair.getKey();
            if(curTime > lasttime){
                break;
            }
            String key = (String) pair.getValue();
            int value = F1.get(key);
            value = value - 1;
            if(value == 0){
                F1.remove(key);
            }
            else {
                F1.put(key, value);
            }
            it.remove();
            it = F2.entrySet().iterator();
        }

        for (Map.Entry<String, Integer> entry : F1.entrySet()) {
            String key = entry.getKey();
            int value = entry.getValue();
            value = -value;

            if(map.containsKey(value)){
                values = map.get(value);
            }
            else {
                values = new ArrayList<String>();
            }
            values.add(key);
        }

        for (Map.Entry<Integer, List<String>> entry : map.entrySet()) {
            for (String val : entry.getValue()) {
                if (result.size() == limit) {
                    return result;
                }
                result.add(val);
            }
        }
        return result;
    }

    /**
     *  Calculates the number of request count
     * @return request count
     */
    public int peakLoad30s() {
        calculateMaximumRequestSlot();
        return maximumRequestSlot;
    }

    /**
     * Updates time for a given key
     * @param key
     */
    public void updateTimeWiseTrack(String key){
        int value = 0;
        if(F1.containsKey(key)){
            value = F1.get(key);
        }
        value = value + 1;
        F1.put(key,value);

        F2.put((System.currentTimeMillis() / 1000),key);
    }

    /**
     * Calculates the maximum number of request
     */
    public void calculateMaximumRequestSlot() {
        int value = 0;
        long timeSlot = (System.currentTimeMillis() / 1000) / 30;
        if(timeSlots.containsKey(timeSlot)){
            value = timeSlots.get(timeSlot);
        }
        value = value + 1;
        timeSlots.put(timeSlot,value);
        if(value > maximumRequestSlot ){
            maximumRequestSlot = value;
        }
    }

    public List<String> executeQuery(String query) {
        return QueryFactory.parse(query);
    }

    public static void main(String[] args){
        WikiMediator wikiMediator = new WikiMediator();

//        System.out.println("***********************      Begin     **************************************\n\n");
//        System.out.println( wikiMediator.search("Chris Piche", 10) );
//        System.out.println( wikiMediator.getPage("Chris Piche") + "\n--------" );
//        System.out.println( wikiMediator.zeitgeist(10) );
//        System.out.println( wikiMediator.trending(10) );
//        System.out.println( "Time = " + wikiMediator.peakLoad30s() );
//        System.out.println("\n\n***********************     The End    **************************************");
    }
}