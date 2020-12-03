package cpen221.mp3;

import cpen221.mp3.fsftbuffer.*;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class Tests {
    private final Entry one = new Entry(1);
    private final Entry two = new Entry(2);
    private final Entry three = new Entry(3);
    private final Entry four = new Entry(4);
    private final Entry five = new Entry(5);

    /*
        You can add your tests here.
        Remember to import the packages that you need, such
        as cpen221.mp3.fsftbuffer.
     */

    /* Task 1 Tests */

    public static class Entry implements Bufferable {
        int content;
        String id;

        public Entry (int content) {
            this (content, Integer.toString(content));
        }

        public Entry (int content, String id) {
            this.content = content;
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String toString() {
            return "Entry{" +
                    "content: " + content +
                    ", id: '" + id + '\'' +
                    '}';
        }
    }

    private boolean isNotInBuffer(FSFTBuffer<Entry> buffer, Entry entry) {
        String expectedErrorMessage = "The entry you want to access is not in the cache";
        boolean errorThrown = false;
        String errorMessage = null;

        try {
            buffer.get(entry.id());
        } catch (IllegalAccessException e) {
            errorMessage = e.getMessage();
            errorThrown = true;
        }

        return errorThrown && errorMessage.equals(expectedErrorMessage);
    }

    private boolean isInBuffer(FSFTBuffer<Entry> buffer, Entry entry) {
        boolean errorThrown = false;
        Entry actualEntry = null;
        try {
            actualEntry = buffer.get(entry.id());
        } catch (IllegalAccessException e) {
            errorThrown = true;
        }

        return !errorThrown && entry == actualEntry;
    }

    private boolean testInAndNotInBuffer(FSFTBuffer<Entry> buffer, List<Entry> inList, List<Entry> notInList) {
        for (Entry entry : inList) {
            if (!isInBuffer(buffer, entry)) {
                return false;
            }
        }
        for (Entry entry : notInList) {
            if (!isNotInBuffer(buffer, entry)) {
                return false;
            }
        }
        return true;
    }

    private void sleep(int time) {
        try {
            Thread.sleep(time * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sleep(double doubleTime) {
        int time = (int) (doubleTime * 1000);
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testEvictionAfterTimeout() {
        int timeout = 3;
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, timeout);
        buffer.put(one);
        buffer.put(two);
        buffer.put(three);

        sleep(timeout);

        assertTrue(isNotInBuffer(buffer, one));
        assertTrue(isNotInBuffer(buffer, two));
        assertTrue(isNotInBuffer(buffer, three));
    }

    @Test
    public void testNonEvictionBeforeTimeout() {
        int timeout = 3;
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, timeout);
        buffer.put(one);
        buffer.put(two);
        buffer.put(three);

        sleep(timeout/2);

        assertTrue(isInBuffer(buffer, one));
        assertTrue(isInBuffer(buffer, two));
        assertTrue(isInBuffer(buffer, three));
    }

    @Test
    public void testForcedEviction1() {
        int timeout = 3;
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, timeout);
        buffer.put(one);
        sleep(timeout/2);
        buffer.put(two);
        buffer.put(three);

        assertTrue( testInAndNotInBuffer(buffer, Arrays.asList(one, two, three), Collections.singletonList(four)) );

        buffer.put(four);

        assertTrue( testInAndNotInBuffer(buffer, Arrays.asList(two, three, four), Collections.singletonList(one)) );
    }

    @Test
    public void testForcedEviction2() {
        int timeout = 2;
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, timeout);

        buffer.put(one);
        sleep(0.2);

        buffer.put(two);
        buffer.put(three);
        sleep(0.3);

        buffer.touch(two.id());
        buffer.touch(one.id());
        sleep(0.2);

        buffer.put(four);
        buffer.put(five);

        assertTrue( testInAndNotInBuffer(buffer, Arrays.asList(one, four, five), Arrays.asList(two, three)) );

    }

    @Test
    public void testForcedEviction3() {
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, 1);

        buffer.put(one);
        buffer.put(two);
        buffer.put(three);

        sleep(0.3);
        boolean touch1 = buffer.touch(one.id());
        Entry updatedTwo = new Entry(100, "2");
        boolean updated1 = buffer.update(updatedTwo);

        sleep(0.5);
        buffer.put(four);
        boolean touch2 = buffer.touch(updatedTwo.id());

        sleep(0.5);
        assertTrue(touch1);
        assertTrue(updated1);
        assertTrue(touch2);
        assertTrue( testInAndNotInBuffer(buffer, Arrays.asList(updatedTwo, four), Arrays.asList(one, three)) );
    }

    @Test
    public void testPutFail() {
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, 2);
        boolean put1 = buffer.put(one);
        boolean put2 = buffer.put(one);

        assertTrue(put1);
        assertFalse(put2);
    }

    @Test
    public void testTouch1() {
        int timeout = 2;
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, timeout);

        buffer.put(one);

        sleep(1.5);
        boolean updated = buffer.touch(one.id());

        sleep(1.5);

        assertTrue(isInBuffer(buffer,one));
        assertTrue(updated);
    }

    @Test
    public void testTouch2() {
        int timeout = 2;
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, timeout);

        buffer.put(one);

        sleep(1.5);
        boolean updated = buffer.touch(one.id());

        sleep(2.0);

        assertTrue(isNotInBuffer(buffer,one));
        assertTrue(updated);

    }

    @Test
    public void testTouchFail() {
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, 2);

        boolean updated = buffer.touch(one.id());

        sleep(2.0);

        assertTrue(isNotInBuffer(buffer,one));
        assertFalse(updated);

    }

    @Test
    public void testUpdate1() {
        int timeout = 2;
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, timeout);

        buffer.put(one);
        sleep(1.0);

        Entry updatedOne = new Entry(12, "1");
        boolean updated = buffer.update(updatedOne);

        assertTrue( isInBuffer(buffer, updatedOne) );
        assertTrue(updated);
    }

    @Test
    public void testUpdateFail() {
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, 2);

        buffer.put(one);

        sleep(3);
        Entry updatedOne = new Entry(13, "1");
        boolean updated = buffer.update(updatedOne);

        assertTrue( isNotInBuffer(buffer, updatedOne) );
        assertFalse(updated);
    }

}
