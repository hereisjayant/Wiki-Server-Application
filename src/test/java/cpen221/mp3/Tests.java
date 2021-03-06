package cpen221.mp3;

import cpen221.mp3.fsftbuffer.*;
import cpen221.mp3.server.WikiMediatorClient;
import cpen221.mp3.server.WikiMediatorServer;
import cpen221.mp3.wikimediator.WikiMediator;

import org.fastily.jwiki.core.Wiki;
import org.fastily.jwiki.dwrap.Contrib;
import org.junit.Test;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;

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
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>();
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

    /* Task 2 Tests */

    @Test
    public void testTwoThreads1() {
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, 1);

        sleep(0.2);

        new Thread(() -> buffer.put(one)).start();

        sleep(0.5);
        assertTrue( isInBuffer(buffer, one) );
    }

    @Test
    public void testTwoThreads2() {
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, 1);

        buffer.put(one);

        sleep(0.5);

        new Thread(() -> assertTrue( isInBuffer(buffer, one))).start();
    }

    @Test
    public void testTwoThreads3() {
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, 1);

        new Thread(() -> buffer.put(one)).start();

        sleep(1);

        assertTrue( isNotInBuffer(buffer, one) );
    }

    @Test
    public void testTwoThreads4() {
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, 1);

        buffer.put(one);

        sleep(1);

        new Thread(() -> assertTrue( isNotInBuffer(buffer, one) )).start();
    }

    @Test
    public void testMultiForcedEviction1() {
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, 1);

        buffer.put(one);

        new Thread(() -> {
            sleep(0.2);
            buffer.put(two);
        }).start();

        new Thread(() -> {
            sleep(0.4);
            buffer.put(three);
            buffer.put(four);
        }).start();

        sleep(0.5);
        assertTrue( testInAndNotInBuffer(buffer, Arrays.asList(two, three, four), Collections.singletonList(one)));

    }

    @Test
    public void testMultiForcedEviction2() {
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, 1);

        new Thread(() -> {
            sleep(0.1);
            /* time = 0.1s */
            buffer.put(two);

            sleep(1.3);
            /*time = 1.4s */
            assertTrue( testInAndNotInBuffer(buffer, Arrays.asList(two, three, five), Arrays.asList(one, four)) );

        }).start();

        new Thread(() -> {
            sleep(0.2);
            /* time = 0.2s */
            buffer.put(three);
            buffer.touch(two.id());

            sleep(1);
            /* time = 1.2s */
            buffer.put(two);
            buffer.put(three);
            buffer.put(five);
        }).start();

        buffer.put(one);
        /* time = 0.3s */
        sleep(0.3);
        buffer.put(four);

    }

    @Test
    public void testMultiForcedEviction3() {
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, 1);

        new Thread(() -> {
            /* time = 0s */
            buffer.put(one);
            buffer.put(two);
            buffer.put(three);

            sleep(1.7);
            /* time = 1.7s */
            buffer.put(one);
            buffer.put(four);
        }).start();

        new Thread(() -> {
            sleep(0.9);
            /* time = 0.9s */
            buffer.put(four);
            buffer.touch(three.id());
            Entry updatedTwo = new Entry(1001, "2");
            buffer.update(updatedTwo);

        }).start();

        sleep(1.5);
        /* time = 1.5s */
        buffer.put(five);

        sleep(0.2);
        /* time = 1.7s */
        assertTrue( testInAndNotInBuffer(buffer, Arrays.asList(five, one, four), Arrays.asList(two, three)) );

    }

    @Test
    public void testMultiPutFail() {
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, 1);

        new Thread(() -> {
            boolean put = buffer.put(one);
            assertTrue(put);
        }).start();

        sleep(0.3);
        boolean put = buffer.put(one);
        assertFalse(put);
    }

    @Test
    public void testMultiUpdateFail() {
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, 1);

        new Thread(() -> {
            sleep(0.5);
            /* time  = 0.5s */
            buffer.put(one);
        }).start();

        /* time = 0s */
        boolean update1 = buffer.update(new Entry(0, "1"));
        assertFalse(update1);

        sleep(0.6);
        /* time = 0.6 */
        boolean update2 = buffer.update(new Entry(0, "1"));
        assertTrue(update2);

    }

    @Test
    public void testMultiTouchFail() {
        FSFTBuffer<Entry> buffer = new FSFTBuffer<>(3, 1);

        new Thread(() -> {
            sleep(0.5);
            /* time  = 0.5s */
            buffer.put(one);
        }).start();

        /* time = 0s */
        boolean touch1 = buffer.touch(one.id());
        assertFalse(touch1);

        sleep(0.6);
        /* time = 0.6 */
        boolean touch2 = buffer.touch(one.id());
        assertTrue(touch2);

    }

    /* Task 3 Tests */

    @Test
    public void testSearchLimit1() {

        WikiMediator wikiMediator = new WikiMediator();
        List<String> titles = wikiMediator.search("Chris Piche",1);

        assertEquals(1,titles.size());
        assertEquals("Chris Piche",titles.get(0));
    }

    @Test
    public void testSearchLimit2() {

        WikiMediator wikiMediator = new WikiMediator();
        List<String> titles = wikiMediator.search("Chris Piche",2);

        assertEquals(2,titles.size());
        assertEquals("Talk:Chris Piche",titles.get(1));
    }

    @Test
    public void testSearchLimit5() {

        WikiMediator wikiMediator = new WikiMediator();

        List<String> expectedTitles = new LinkedList<>(Arrays.asList("Chris Piche", "Talk:Chris Piche",
                        "Eyeball Chat", "Bents, Saskatchewan", "Temiscaming Royals"));

        List<String> titles = wikiMediator.search("Chris Piche",5);

        assertEquals(5,titles.size());
        assertEquals(expectedTitles,titles);
    }

    @Test
    public void testSearchExpandingLimit() {
        WikiMediator wikiMediator = new WikiMediator();
        List<String> titles2 = wikiMediator.search("Chris Piche", 2);
        List<String> expectedTitles2 = new LinkedList<>(Arrays.asList("Chris Piche", "Talk:Chris Piche"));
        assertEquals(expectedTitles2, titles2);

        List<String> titles5 = wikiMediator.search("Chris Piche", 5);
        List<String> expectedTitles5 = new LinkedList<>(Arrays.asList("Chris Piche", "Talk:Chris Piche",
                "Eyeball Chat", "Bents, Saskatchewan", "Temiscaming Royals"));
        assertEquals(titles5, expectedTitles5);
    }

    @Test
    public void testSearchShrinkingLimit() {
        WikiMediator wikiMediator = new WikiMediator();
        List<String> titles5 = wikiMediator.search("Chris Piche", 5);
        List<String> expectedTitles5 = new LinkedList<>(Arrays.asList("Chris Piche", "Talk:Chris Piche",
                "Eyeball Chat", "Bents, Saskatchewan", "Temiscaming Royals"));
        assertEquals(titles5, expectedTitles5);

        List<String> titles2 = wikiMediator.search("Chris Piche", 2);
        List<String> expectedTitles2 = new LinkedList<>(Arrays.asList("Chris Piche", "Talk:Chris Piche"));
        assertEquals(expectedTitles2, titles2);
    }

    @Test
    public void testGetPages1() {
        WikiMediator wikiMediator = new WikiMediator();

        String expectedText = "This is the page corresponding to the user Vijeethvp.\n" +
                "This is used largely for testing purposes of a software project.\n" +
                "\n" +
                "[[Category: Student]]";

        String pageText = wikiMediator.getPage("User:Vijeethvp");

        assertEquals(expectedText, pageText);
    }

    @Test
    public void testGetPages2() {
        /* White box testing */
        WikiMediator wikiMediator = new WikiMediator();

        String expectedText = "This is the page corresponding to the user Vijeethvp.\n" +
                "This is used largely for testing purposes of a software project.\n" +
                "\n" +
                "[[Category: Student]]";

        String pageText = wikiMediator.getPage("User:Vijeethvp");
        assertEquals(expectedText, pageText);

        /* This pageText2 will be retrieved from the cache */
        String pageText2 = wikiMediator.getPage("User:Vijeethvp");
        assertEquals(expectedText, pageText2);
    }

    @Test
    public void testZeitgeist1() {
        WikiMediator wikiMediator = new WikiMediator();

        wikiMediator.search("Hello", 10);
        sleep(0.5);
        wikiMediator.getPage("User:Vijeethvp");
        sleep(0.5);
        wikiMediator.search("Hello", 4);
        sleep(0.5);
        wikiMediator.search("Hello", 3);
        sleep(0.5);
        wikiMediator.getPage("User:Vijeethvp");
        sleep(0.5);
        wikiMediator.search("Hello", 4);
        sleep(0.5);
        wikiMediator.getPage("User:AllyD");
        sleep(0.5);


        List<String> expectedList = new LinkedList<>(Arrays.asList("Hello", "User:Vijeethvp", "User:AllyD"));
        List<String> actualList = wikiMediator.zeitgeist(5);

        assertEquals(expectedList, actualList);
    }

    @Test
    public void testZeitgeist2() {
        WikiMediator wikiMediator = new WikiMediator();

        wikiMediator.search("Hello", 10);
        wikiMediator.getPage("User:Vijeethvp");
        wikiMediator.search("Hello", 4);
        wikiMediator.search("Hello", 3);
        wikiMediator.getPage("User:Vijeethvp");
        wikiMediator.search("Hello", 4);
        wikiMediator.getPage("User:AllyD");


        List<String> expectedList = new LinkedList<>(Arrays.asList("Hello", "User:Vijeethvp", "User:AllyD"));
        List<String> actualList = wikiMediator.zeitgeist(5);

        assertEquals(expectedList, actualList);
    }

    @Test
    public void testZeitgeist3() {
        WikiMediator wikiMediator = new WikiMediator();

        wikiMediator.search("Hello", 10);
        wikiMediator.getPage("User:Vijeethvp");
        wikiMediator.search("Hello", 4);
        sleep(0.1);
        wikiMediator.search("Hello", 3);
        wikiMediator.getPage("User:Vijeethvp");
        wikiMediator.search("Hello", 4);
        wikiMediator.getPage("User:AllyD");
        sleep(0.4);
        wikiMediator.search("Blue", 10);
        wikiMediator.getPage("User:Vijeethvp");
        wikiMediator.search("Blue", 4);
        wikiMediator.search("Blue", 3);
        wikiMediator.getPage("User:Vijeethvp");
        sleep(0.3);
        wikiMediator.search("Hello", 4);
        wikiMediator.getPage("User:AllyD");


        List<String> expectedList = new LinkedList<>(Arrays.asList("Hello", "User:Vijeethvp", "Blue", "User:AllyD"));
        List<String> actualList = wikiMediator.zeitgeist(5);

        assertEquals(expectedList, actualList);

        List<String> expectedList2 = new LinkedList<>(Arrays.asList("Hello", "User:Vijeethvp"));
        List<String> actualList2 = wikiMediator.zeitgeist(2);
        assertEquals(expectedList2, actualList2);
    }

    @Test
    public void testsearchLimit6() {
        WikiMediator wikiMediator = new WikiMediator();
        String expected = "45th president of the United State";
        String actual = wikiMediator.getPage("Donald Trump");

        assertEquals(expected,actual.substring(20,54));
    }

    @Test
    public void testsearchLimit7() {
        WikiMediator wikiMediator = new WikiMediator();
        char expected = ' ';
        String actual = wikiMediator.getPage("Barack Obama");

        assertEquals(expected,actual.charAt(30));
    }

    @Test
    public void testTrending1() {
        WikiMediator wikiMediator = new WikiMediator();

        wikiMediator.search("Hello", 10);
        wikiMediator.getPage("User:Vijeethvp");
        sleep(0.25);
        wikiMediator.search("Blue", 4);
        sleep(0.25);
        wikiMediator.search("Hello", 3);
        wikiMediator.getPage("User:Vijeethvp");
        sleep(0.25);
        wikiMediator.search("Hello", 4);
        wikiMediator.getPage("User:AllyD");
        sleep(0.25);
        wikiMediator.getPage("User:AllyD");

        List<String> expectedList = new LinkedList<>(Arrays.asList("User:AllyD", "Hello", "User:Vijeethvp", "Blue"));
        List<String> actualList = wikiMediator.trending(10);

    }

    @Test
    public void testTrending2() {
        WikiMediator wikiMediator = new WikiMediator();

        wikiMediator.search("Hello", 10);
        wikiMediator.getPage("User:Vijeethvp");
        sleep(0.25);
        wikiMediator.search("Blue", 4);
        sleep(0.25);
        wikiMediator.search("Hello", 3);
        wikiMediator.getPage("User:Vijeethvp");
        sleep(0.25);
        wikiMediator.search("Hello", 4);
        wikiMediator.getPage("User:AllyD");
        sleep(0.25);
        wikiMediator.getPage("User:AllyD");

        List<String> expectedList = new LinkedList<>(Collections.emptyList());
        List<String> actualList = wikiMediator.trending(0);

        assertEquals(expectedList, actualList);
    }

    @Test
    public void testPeakLoad30s1() {
        WikiMediator wikiMediator = new WikiMediator();

        wikiMediator.search("Hello", 10);
        wikiMediator.getPage("User:Vijeethvp");
        sleep(0.25);
        wikiMediator.search("Blue", 4);
        sleep(0.25);
        wikiMediator.search("Hello", 3);
        wikiMediator.getPage("User:Vijeethvp");
        sleep(0.25);
        wikiMediator.search("Hello", 4);
        wikiMediator.getPage("User:AllyD");
        sleep(0.25);
        wikiMediator.getPage("User:AllyD");
        wikiMediator.trending(0);
        sleep(0.1);
        wikiMediator.zeitgeist(2);

        assertEquals(11, wikiMediator.peakLoad30s());

    }

    @Test
    public void testPeakLoad30s2() {
        WikiMediator wikiMediator = new WikiMediator();

        wikiMediator.search("Hello", 10);
        wikiMediator.getPage("User:Vijeethvp");
        sleep(0.25);
        wikiMediator.search("Blue", 4);
        sleep(0.25);
        wikiMediator.search("Hello", 3);
        wikiMediator.getPage("User:Vijeethvp");
        wikiMediator.trending(0);
        sleep(0.1);
        wikiMediator.zeitgeist(2);
        wikiMediator.executeQuery("get page where title is 'Hello' ");

        assertEquals(9, wikiMediator.peakLoad30s());

    }




    /* Task 5 Tests */

    public static final String author1 = "2A00:23C7:2183:BE00:6C16:ABEA:9039:3A8";
    public static final String author2 = "64.49.112.19";

    private List<String> pagesAuthoredBy(String author, Wiki wiki) {
        List<String> expectedList = new LinkedList<>();
        ArrayList<Contrib> contribs = wiki.getContribs(author,-1,true,false);
        for (Contrib contrib : contribs) {
            String titleOfContribution = contrib.title;
            String lastEditor = wiki.getLastEditor(titleOfContribution);
            if (lastEditor.equals(author)) {
                expectedList.add(titleOfContribution);
            }
        }
        return expectedList;
    }

    private List<String> getAuthorsOf(List<String> titles, Wiki wiki) {
        List<String> tempResponse = new LinkedList<>();
        for (String pageTitle : titles) {
            tempResponse.add(wiki.getLastEditor(pageTitle));
        }
        return tempResponse;
    }

    public List<String> union (List<String> l1, List<String> l2) {
        Set<String> s1 = new HashSet<>(l1);
        Set<String> s2 = new HashSet<>(l2);
        s1.addAll(s2);
        return new LinkedList<>(s1);
    }

    @Test
    public void testSturcturedQueryGetPage1() {
        WikiMediator wikiMediator = new WikiMediator();
        List<String> expectedList = new LinkedList<>(Collections.singleton("Hello"));
        List<String> actualList = wikiMediator.executeQuery("get page where title is 'Hello' ");
        assertEquals(expectedList,actualList);
    }

    @Test
    public void testSturcturedQueryGetPage2() {
        WikiMediator wikiMediator = new WikiMediator();
        Wiki wiki = new Wiki.Builder().build();
        List<String> expectedList = wiki.getCategoryMembers("Illinois state senators");
        List<String> actualList = wikiMediator.executeQuery("get page where category is 'Illinois state senators' ");

        assertEquals(expectedList.size(),actualList.size());
        assertEquals(new HashSet<>(expectedList), new HashSet<>(actualList));
    }

    @Test
    public void testStructuredQueryGetPage3() {
        WikiMediator wikiMediator = new WikiMediator();
        Wiki wiki = new Wiki.Builder().build();

        List<String> expectedList = Collections.singletonList("User:Vijeethvp");
        List<String> actualList = wikiMediator.executeQuery("get page where author is 'Vijeethvp' ");

        assertEquals(expectedList.size(),actualList.size());
        assertEquals(new HashSet<>(expectedList), new HashSet<>(actualList));
    }

    @Test
    public void testStructuredQueryGetAuthor1() {
        WikiMediator wikiMediator = new WikiMediator();
        Wiki wiki = new Wiki.Builder().build();

        List<String> list1 = getAuthorsOf(Collections.singletonList("Barack Obama"),wiki);
        List<String> list2 = getAuthorsOf(wiki.getCategoryMembers("Fortnite"), wiki);
        List<String> expectedList = union(list1, list2);
        List<String> actualList = wikiMediator.executeQuery("get author where (title is 'Barack Obama' or category is 'Fortnite') ");

        assertEquals(expectedList.size(),actualList.size());
        assertEquals(new HashSet<>(expectedList), new HashSet<>(actualList));
    }

    @Test
    public void testStructuredQueryGetAuthor2() {
        WikiMediator wikiMediator = new WikiMediator();
        Wiki wiki = new Wiki.Builder().build();

        List<String> expectedList = new LinkedList<>(Arrays.asList("Vijeethvp", "Wikignome Wintergreen"));
        List<String> actualList = wikiMediator.executeQuery("get author where (title is 'Wild Fields' or  title is 'User:Vijeethvp') ");

        assertEquals(expectedList.size(),actualList.size());
        assertEquals(new HashSet<>(expectedList), new HashSet<>(actualList));
    }

    @Test
    public void testStructuredQueryGetAuthor3() {
        WikiMediator wikiMediator = new WikiMediator();
        Wiki wiki = new Wiki.Builder().build();

        List<String> expectedList = new LinkedList<>(Collections.singletonList("Vijeethvp"));
        List<String> actualList = wikiMediator.executeQuery("get author where author is 'Vijeethvp' ");

        assertEquals(expectedList.size(),actualList.size());
        assertEquals(new HashSet<>(expectedList), new HashSet<>(actualList));
    }

    @Test
    public void testStructuredQueryGetCategory1() {
        WikiMediator wikiMediator = new WikiMediator();
        Wiki wiki = new Wiki.Builder().build();

        List<String> expectedList = Collections.singletonList("Category:Student");
        List<String> actualList = wikiMediator.executeQuery("get category where (author is 'Vijeethvp' and (title is 'User:Vijeethvp' or title is 'Naomi Klein')) ");

        assertEquals(expectedList.size(),actualList.size());
        assertEquals(new HashSet<>(expectedList), new HashSet<>(actualList));
    }

    @Test
    public void testStructuredQueryGetCategory2() {
        WikiMediator wikiMediator = new WikiMediator();
        Wiki wiki = new Wiki.Builder().build();

        List<String> expectedList = Collections.emptyList();
        List<String> actualList = wikiMediator.executeQuery("get category where (author is 'Vijeethvp' and title is 'Naomi Klein') ");

        assertEquals(0,actualList.size());
        assertEquals(new HashSet<>(expectedList), new HashSet<>(actualList));
    }

    @Test
    public void testStructuredQuerySortedAsc() {
        WikiMediator wikiMediator = new WikiMediator();
        Wiki wiki = new Wiki.Builder().build();

        List<String> expectedList = new LinkedList<>(Arrays.asList("Vijeethvp", "Wikignome Wintergreen"));
        List<String> actualList = wikiMediator.executeQuery("get author where (title is 'Wild Fields' or  title is 'User:Vijeethvp') asc ");

        assertEquals(expectedList, actualList);
    }

    @Test
    public void testStructuredQuerySortedDesc() {
        WikiMediator wikiMediator = new WikiMediator();
        Wiki wiki = new Wiki.Builder().build();

        List<String> expectedList = new LinkedList<>(Arrays.asList("Wikignome Wintergreen", "Vijeethvp"));
        List<String> actualList = wikiMediator.executeQuery("get author where (title is 'Wild Fields' or  title is 'User:Vijeethvp') desc ");

        assertEquals(expectedList, actualList);
    }

    /* Task 4 Tests */
    public static final int WIKI_PORT = 4949;


    @Test
    public void ServerTest() {
        new Thread(() -> {
            try {
                WikiMediatorServer server = new WikiMediatorServer(
                        WIKI_PORT, 10);
                server.serve();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(() -> {
            try {
                WikiMediatorClient client =
                        new WikiMediatorClient("localhost", WikiMediatorServer.WIKI_PORT);
                client.sendRequest("{" +
                        "\t\"id\": \"ten\"," +
                        "\t\"type\": \"search\"," +
                        "\t\"query\": \"Barack Obama\"," +
                        "\t\"limit\": \"10\"" +
                        "}");

                String y = client.getReply();
                System.out.println("Result" + y);

                client.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();


    }

    @Test
    public void ServerTest3TimeOut() {
        new Thread(() -> {
            try {
                WikiMediatorServer server = new WikiMediatorServer(
                        WIKI_PORT, 10);
                server.serve();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(() -> {
            try {
                WikiMediatorClient client =
                        new WikiMediatorClient("localhost", WikiMediatorServer.WIKI_PORT);
                client.sendRequest("{" +
                        "\t\"id\": \"ten\"," +
                        "\t\"type\": \"stop\"," +
                        "\t\"query\": \"Barack Obama\"," +
                        "\t\"limit\": \"1\"" +
                        "}");

                String y = client.getReply();
                System.out.println("Result" + y);

                client.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();


    }

    @Test
    public void ServerTest2stop() {
        new Thread(() -> {
            try {
                WikiMediatorServer server = new WikiMediatorServer(
                        WIKI_PORT, 10);
                server.serve();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(() -> {
            try {
                WikiMediatorClient client =
                        new WikiMediatorClient("localhost", WikiMediatorServer.WIKI_PORT);
                client.sendRequest("{" +
                        "\t\"id\": \"ten\"," +
                        "\t\"type\": \"stop\"," +
                        "\t\"query\": \"Barack Obama\"," +
                        "\t\"limit\": \"10\"" +
                        "}");

                String y = client.getReply();
                System.out.println("Result" + y);

                client.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();


    }

    @Test
    public void ServerTest4Trending() {
        new Thread(() -> {
            try {
                WikiMediatorServer server = new WikiMediatorServer(
                        WIKI_PORT, 10);
                server.serve();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(() -> {
            try {
                WikiMediatorClient client =
                        new WikiMediatorClient("localhost", WikiMediatorServer.WIKI_PORT);
                client.sendRequest("{" +
                        "\t\"id\": \"ten\"," +
                        "\t\"type\": \"trending\"," +
                        "\t\"limit\": \"10\"" +
                        "}");

                String y = client.getReply();
                System.out.println("Result" + y);

                client.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();


    }


}
