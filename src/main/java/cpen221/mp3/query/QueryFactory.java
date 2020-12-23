package cpen221.mp3.query;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.fastily.jwiki.core.Wiki;
import org.fastily.jwiki.dwrap.Contrib;
import org.fastily.jwiki.dwrap.Revision;
import query.QueryBaseListener;
import query.QueryLexer;
import query.QueryParser;

import java.util.*;

public class QueryFactory {

    public static List<String> parse (String string) {
        // Create a stream of tokens using the lexer.
        CharStream stream = new ANTLRInputStream(string);
        QueryLexer lexer = new QueryLexer(stream);
        lexer.reportErrorsAsExceptions();
        TokenStream tokens = new CommonTokenStream(lexer);

        // Feed the tokens into the parser.
        QueryParser parser = new QueryParser(tokens);
        parser.reportErrorsAsExceptions();

        // Generate the parse tree using the starter rule.
        ParseTree tree = parser.query(); // "root" is the starter rule.

        new ParseTreeWalker().walk(new QueryListener_PrintEverything(), tree);

        // Finally, construct a Query value by walking over the parse tree.
        ParseTreeWalker walker = new ParseTreeWalker();
        QueryListener_QueryCreator listener = new QueryListener_QueryCreator();
        walker.walk(listener, tree);

        // return the Document value that the listener created
        return listener.getQuery();
    }

    private static class QueryListener_QueryCreator extends QueryBaseListener {
        List<Set<String>> listOfSetsOfPages = new ArrayList<>();
        List<String> response;
        Wiki wiki = new Wiki.Builder().build();

        @Override
        public void exitSimple_condition(QueryParser.Simple_conditionContext ctx) {
            if (ctx.TITLE() != null) {
                Set<String> setOfPageTitle = new HashSet<>();

                String string = ctx.STRING().getText();
                assert string.charAt(0) == '\'';
                assert string.charAt(string.length() - 1) == '\'';

                setOfPageTitle.add(string.substring(1, string.length() - 1));
                listOfSetsOfPages.add(setOfPageTitle);
            }
            else if (ctx.AUTHOR() != null) {
                Set<String> setOfPageTitle = new HashSet<>();
                String author = ctx.STRING().getText();
                assert author.charAt(0) == '\'';
                assert author.charAt(author.length() - 1) == '\'';
                author = author.substring(1, author.length() - 1);

                ArrayList<Contrib> contribs = wiki.getContribs(author,-1,true,false);
                for (Contrib contrib : contribs) {
                    String titleOfContribution = contrib.title;
                    String lastEditor = wiki.getLastEditor(titleOfContribution);
                    if (lastEditor.equals(author)) {
                        setOfPageTitle.add(titleOfContribution);
                    }
                }
                listOfSetsOfPages.add(setOfPageTitle);
            }
            else {
                assert ctx.CATEGORY() != null;
                String category = /*"Category:" +*/ ctx.STRING().getText();
                assert category.charAt(0) == '\'';
                assert category.charAt(category.length() - 1) == '\'';
                category = category.substring(1, category.length() - 1);

                ArrayList<String> titlesInCategory = wiki.getCategoryMembers(category);
                Set<String> setOfPageTitle = new HashSet<>(titlesInCategory);
                listOfSetsOfPages.add(setOfPageTitle);
            }
        }

        @Override
        public void exitCondition(QueryParser.ConditionContext ctx) {
            if (ctx.AND() != null) {
                assert listOfSetsOfPages.size() >= 2;
                Set<String> lastPageTitleSet = listOfSetsOfPages.remove(listOfSetsOfPages.size() - 1);
                Set<String> secondLastPageTitleSet = listOfSetsOfPages.get(listOfSetsOfPages.size() - 1);
                try {
                    secondLastPageTitleSet.retainAll(lastPageTitleSet);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else if (ctx.OR() != null) {
                assert listOfSetsOfPages.size() >= 2;
                Set<String> lastPageTitleSet = listOfSetsOfPages.remove(listOfSetsOfPages.size() - 1);
                Set<String> secondLastPageTitleSet = listOfSetsOfPages.get(listOfSetsOfPages.size() - 1);
                try {
                    secondLastPageTitleSet.addAll(lastPageTitleSet);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void exitQuery(QueryParser.QueryContext ctx) {
            assert listOfSetsOfPages.size() == 1;
            assert response == null;

            if (ctx.ITEM().getText().equals("page")) {
                List<String> tempResponse = new LinkedList<>(listOfSetsOfPages.get(0));
                response = removeDuplicates(tempResponse);
            }
            else if (ctx.ITEM().getText().equals("author")) {
                List<String> tempResponse = new LinkedList<>();
                for (String pageTitle : listOfSetsOfPages.get(0)) {
                    tempResponse.add(wiki.getLastEditor(pageTitle));
                }
                response = removeDuplicates(tempResponse);
            }
            else {
                List<String> tempResponse = new LinkedList<>();
                for (String pageTitle : listOfSetsOfPages.get(0)) {
                    ArrayList<String> listOfCategories = wiki.getCategoriesOnPage(pageTitle);
                    try {
                        tempResponse.addAll(listOfCategories);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                response = removeDuplicates(tempResponse);
            }

            if (ctx.SORTED() != null) {
                assert ctx.SORTED().getText().equals("asc") || ctx.SORTED().getText().equals("desc");

                boolean ascending = ctx.SORTED().getText().equals("asc");
                Collections.sort(response, new Comparator<String>() {
                    @Override
                    public int compare(String o1, String o2) {
                        if (ascending) {
                            return o1.compareTo(o2);
                        }
                        return o2.compareTo(o1);
                    }
                });
            }
        }

        private List<String> removeDuplicates (List<String> list) {
            Set<String> set = new HashSet<>(list);
            return new LinkedList<>(set);
        }

        /**
         * Returns the response of the structured query
         *
         * Precondition: walker.walk(listener, tree); must be called before calling this function
         * @return the response of the query
         */
        public List<String> getQuery() {
            assert response != null;
            return response;
        }
    }

    private static class QueryListener_PrintEverything extends QueryBaseListener {
        public void enterQuery(QueryParser.QueryContext ctx) {
            System.err.println("entering Query: " + ctx.getText());
        }

        public void exitQuery(QueryParser.QueryContext ctx) {
            System.err.println("exiting Query: " + ctx.getText());
        }

        public void enterCondition(QueryParser.ConditionContext ctx) {
            System.err.println("entering Condition: " + ctx.getText());
        }

        public void exitCondition(QueryParser.ConditionContext ctx) {
            System.err.println("exiting Condition: " + ctx.getText());
            System.err.println("    AND: " + ctx.AND());
            System.err.println("    OR: " + ctx.OR());
        }

        public void enterSimple_condition(QueryParser.Simple_conditionContext ctx) {
            System.err.println("entering Simple_condition: " + ctx.getText());
        }

        public void exitSimple_condition(QueryParser.Simple_conditionContext ctx) {
            System.err.println("exiting Simple_condition: " + ctx.getText());
            System.err.println("    TITLE: " + ctx.TITLE());
            System.err.println("    AUTHOR: " + ctx.AUTHOR());
            System.err.println("    CATEGORY: " + ctx.CATEGORY());
            System.err.println("    STRING: " + ctx.STRING().getText());
        }
    }
}
