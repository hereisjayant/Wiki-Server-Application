package cpen221.mp3.fsftbuffer;

import java.util.*;

public class FSFTBuffer<T extends Bufferable> {

    /* the default buffer size is 32 objects */
    public static final int DSIZE = 32;

    /* the default timeout value is 3600s */
    public static final int DTIMEOUT = 3600;

    /* TODO: Implement this datatype */

    private List<T> bufferList;

    private Map<String, Long> timeMap;

    private int capacity, timeout;

    /*
        Abstraction Function:
            bufferList represents the finite-space finite-time buffer such that bufferList[i] is less
                recently used than bufferList[i+1].

            timeMap contains the mapping between the id of each object in the bufferList and
                the time (in milli-seconds) when that object was added to the bufferList.

            capacity represents the total number of objects that the buffer can hold

            timeout represents the time (in seconds) that any unused object will be retained for

        Representation Invariant:
            bufferList should be non-null and each object in bufferList should be non-null

            for every object O in bufferList, timeMap must contain key of O.id()

            the size of bufferList must equal the size of timeMap, and both the sizes must be
                less than or equal to capacity

            for every (id, time of Addition) pair in timeMap, current time < time of Addition + timeout * 1000

            for every legal value of i, timeMap maps bufferList[i].id() to a time that is less than or
                before the time that timeMap maps bufferList[i+1].id() to. This is because bufferList[i]
                is less recently used than bufferList[i+1].

            capacity and timeout must both be positive integers
     */


    /**
     * Check the rep invariant
     *
     * effects: nothing if this satisfies the rep invariant otherwise throws an exception
     */
    private void checkRep() {

        if (!debug)
            return;

        assert bufferList != null;

        for (T entry : bufferList) {
            assert entry != null;
            assert timeMap.containsKey(entry.id());
        }

        assert bufferList.size() == timeMap.size();

        assert bufferList.size() <= capacity;

        long currentTime = System.currentTimeMillis();
        for (Long time : timeMap.values()) {
            assert  currentTime < time + timeout * 1000;
        }

        for (int i = 0; i < bufferList.size() - 1; i++) {
            T thisEntry = bufferList.get(i);
            T nextEntry = bufferList.get(i+1);
            Long thisEntryTime = timeMap.get(thisEntry.id());
            Long nextEntryTime = timeMap.get(nextEntry.id());
            assert thisEntryTime <= nextEntryTime;
        }

        assert capacity > 0 && timeout > 0;

    }

    /* Variable to control checkRep invoking */
    static boolean debug = true;

    /**
     * Create a buffer with a fixed capacity and a timeout value.
     * Objects in the buffer that have not been refreshed within the
     * timeout period are removed from the cache.
     *
     * @param capacity the number of objects the buffer can hold
     * @param timeout  the duration, in seconds, an object should
     *                 be in the buffer before it times out
     */
    public FSFTBuffer(int capacity, int timeout) {
        // TODO: implement this constructor
        this.capacity = capacity;
        this.timeout = timeout;
        bufferList = new LinkedList<>();
        timeMap = new HashMap<>();

        checkRep();
    }

    /**
     * Create a buffer with default capacity and timeout values.
     */
    public FSFTBuffer() {
        this(DSIZE, DTIMEOUT);
    }

    /**
     * Add a value to the buffer.
     * If the buffer is full then remove the least recently accessed
     * object to make room for the new object.
     */
    public boolean put(T t) {
        // TODO: implement this method
        evictEntries();
        checkRep();

        if(timeMap.containsKey(t.id())) {
            return false;
        }

        if (bufferList.size() == capacity) {
            T removalEntry = bufferList.get(0);
            bufferList.remove(removalEntry);
            timeMap.remove(removalEntry.id());
        }

        timeMap.put(t.id(), System.currentTimeMillis());
        bufferList.add(t);

        evictEntries();
        checkRep();
        return true;
    }

    /**
     * @param id the identifier of the object to be retrieved
     * @return the object that matches the identifier from the
     * buffer
     */
    public T get(String id) throws IllegalAccessException{
        /* TODO: change this */
        /* Do not return null. Throw a suitable checked exception when an object
            is not in the cache. You can add the checked exception to the method
            signature. */
        evictEntries();
        checkRep();

        if (!touch(id)) {
            throw new IllegalAccessException("The entry you want to access is not in the cache");
        }

        evictEntries();
        checkRep();
        return getEntryFromID(id);
    }

    /**
     * Requires that the Bufferable object with identifier id is located in the bufferList
     *
     * @param id the indentifier of the Bufferable object
     * @return the corresponding Bufferable object with id that is placed in bufferList
     */
    private T getEntryFromID(String id) {
        int index = getListIndexFromID(id);
        return bufferList.get(index);
    }

    /**
     * Requires that the Bufferable object with identifier id is located in the bufferList
     *
     * @param id the indentifier of the Bufferable object
     * @return the index at which the Bufferable object is placed in bufferList
     */
    private int getListIndexFromID(String id) {
        int index = 0;
        for (T entry : bufferList) {
            if (entry.id().equals(id)) {
                index = bufferList.indexOf(entry);
            }
        }
        return index;
    }


    /**
     * Update the last refresh time for the object with the provided id.
     * This method is used to mark an object as "not stale" so that its
     * timeout is delayed.
     *
     * @param id the identifier of the object to "touch"
     * @return true if successful and false otherwise
     */
    public boolean touch(String id) {
        /* TODO: Implement this method */
        evictEntries();
        checkRep();

        if (!timeMap.containsKey(id)) {
            return false;
        }
        timeMap.put(id, System.currentTimeMillis());

        T entryToRetrieve = getEntryFromID(id);
        bufferList.remove(entryToRetrieve);
        bufferList.add(entryToRetrieve);

        evictEntries();
        checkRep();
        return true;
    }

    /**
     * Update an object in the buffer.
     * This method updates an object and acts like a "touch" to
     * renew the object in the cache.
     *
     * @param t the object to update
     * @return true if successful and false otherwise
     */
    public boolean update(T t) {
        /* TODO: implement this method */
        evictEntries();
        checkRep();

        if (!touch(t.id())) {
            return false;
        }

        int index = getListIndexFromID(t.id());
        bufferList.set(index, t);

        evictEntries();
        checkRep();
        return true;
    }

    /**
     * Evicts objects that have passed the timeout from the bufferList
     */
    private void evictEntries() {
        Long currentTime = System.currentTimeMillis();
        List<String> ids = new LinkedList<>();
        for (String id : timeMap.keySet()) {
            Long entryTime = timeMap.get(id);
            if (currentTime - entryTime>= timeout * 1000) {
                ids.add(id);
            }
        }

        for (String id : ids) {
            timeMap.remove(id);
            bufferList.removeIf(entry -> entry.id().equals(id));
        }
    }

    @Override
    public String toString() {
        return "FSFTBuffer{" +
                "bufferList: " + bufferList +
                '}';
    }
}
