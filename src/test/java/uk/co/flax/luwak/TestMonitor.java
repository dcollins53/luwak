package uk.co.flax.luwak;

import com.google.common.collect.ImmutableList;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.Version;
import org.fest.assertions.api.Assertions;
import org.junit.Test;
import uk.co.flax.luwak.impl.MatchAllDocsQueryFactory;
import uk.co.flax.luwak.impl.SingleFieldInputDocument;

import java.util.List;

import static org.fest.assertions.api.Assertions.fail;
import static uk.co.flax.luwak.util.MatchesAssert.assertThat;

/**
 * Copyright (c) 2013 Lemur Consulting Ltd.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class TestMonitor {

    static class BasicInputDocument extends SingleFieldInputDocument {

        BasicInputDocument(String id, String text) {
            super(id, textfield, text, new WhitespaceAnalyzer(Version.LUCENE_50));
        }

    }

    static final String textfield = "textfield";

    @Test
    public void singleTermQueryMatchesSingleDocument() {

        String document = "This is a test document";

        Query query = new TermQuery(new Term(textfield, "test"));
        MonitorQuery mq = new MonitorQuery("query1", query);

        InputDocument doc = new BasicInputDocument("doc1", document);

        Monitor monitor = new Monitor(mq);

        assertThat(monitor.match(doc))
                .matches("doc1")
                .hasMatchCount(1)
                .matchesQuery("query1")
                    .withHitCount(1)
                    .inField(textfield)
                        .withHit(new QueryMatch.Hit(3, 10, 3, 14));

    }

    @Test(expected = IllegalStateException.class)
    public void monitorWithNoQueriesThrowsException() {
        Monitor monitor = new Monitor();
        InputDocument doc = new BasicInputDocument("doc1", "test");
        monitor.match(doc);
        fail("Monitor with no queries should have thrown an IllegalStateException");
    }

    static class MultiFieldInputDocument extends InputDocument {

        public MultiFieldInputDocument(String id) {
            super(id, new MatchAllDocsQueryFactory());
        }

        public void addField(String field, String text) {
            index.addField(field, text, new WhitespaceAnalyzer(Version.LUCENE_50));
        }

    }

    @Test
    public void multiFieldQueryMatches() {

        MultiFieldInputDocument doc = new MultiFieldInputDocument("doc1");
        doc.addField("field1", "this is a test of field one");
        doc.addField("field2", "and this is an additional test");

        BooleanQuery bq = new BooleanQuery();
        bq.add(new TermQuery(new Term("field1", "test")), BooleanClause.Occur.SHOULD);
        bq.add(new TermQuery(new Term("field2", "test")), BooleanClause.Occur.SHOULD);
        MonitorQuery mq = new MonitorQuery("query1", bq);

        Monitor monitor = new Monitor(mq);
        assertThat(monitor.match(doc))
                .matchesQuery("query1")
                    .inField("field1")
                        .withHit(new QueryMatch.Hit(3, 10, 3, 14))
                    .inField("field2")
                        .withHit(new QueryMatch.Hit(5, 26, 5, 30));

    }

    @Test
    public void testHighlighterQuery() {

        InputDocument docWithMatch = new BasicInputDocument("1", "this is a test document");
        InputDocument docWithNoMatch = new BasicInputDocument("2", "this is a document");
        InputDocument docWithNoHighlighterMatch = new BasicInputDocument("3", "this is a test");

        MonitorQuery mq = new MonitorQuery("1", new TermQuery(new Term(textfield, "test")),
                                                new TermQuery(new Term(textfield, "document")));

        Monitor monitor = new Monitor(mq);
        assertThat(monitor.match(docWithMatch))
                .matchesQuery("1")
                    .inField(textfield)
                        .withHit(new QueryMatch.Hit(4, 15, 4, 23));
        assertThat(monitor.match(docWithNoMatch))
                .doesNotMatchQuery("1");
        assertThat(monitor.match(docWithNoHighlighterMatch))
                .matchesQuery("1").inField(textfield)
                    .withHit(new QueryMatch.Hit(3, 10, 3, 14));

    }

    @Test
    public void canAddMonitorQuerySubclasses() {

        class TestQuery extends MonitorQuery {
            public TestQuery(String id, String query) {
                super(id, new TermQuery(new Term("field", query)));
            }
        };

        List<TestQuery> queries = ImmutableList.of(new TestQuery("1", "foo"), new TestQuery("2", "bar"));
        Monitor monitor = new Monitor(queries);
        monitor.update(queries);

        Assertions.assertThat(monitor.getQuery("1"))
                .isNotNull()
                .isInstanceOf(TestQuery.class);

    }

}
