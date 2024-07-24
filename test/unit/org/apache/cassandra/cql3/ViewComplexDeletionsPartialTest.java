/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.cql3;

import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;

import org.apache.cassandra.Util;
import org.apache.cassandra.db.Keyspace;

/* ViewComplexTest class has been split into multiple ones because of timeout issues (CASSANDRA-16670, CASSANDRA-17167)
 * Any changes here check if they apply to the other classes:
 * - ViewComplexUpdatesTest
 * - ViewComplexDeletionsTest
 * - ViewComplexTTLTest
 * - ViewComplexTest
 * - ViewComplexLivenessTest
 * - ...
 * - ViewComplex*Test
 */
public class ViewComplexDeletionsPartialTest extends ViewAbstractParameterizedTest
{
    // for now, unselected column cannot be fully supported, see CASSANDRA-11500
    @Ignore
    @Test
    public void testPartialDeleteUnselectedColumnWithFlush() throws Throwable
    {
        testPartialDeleteUnselectedColumn(true);
    }

    // for now, unselected column cannot be fully supported, see CASSANDRA-11500
    @Ignore
    @Test
    public void testPartialDeleteUnselectedColumnWithoutFlush() throws Throwable
    {
        testPartialDeleteUnselectedColumn(false);
    }

    private void testPartialDeleteUnselectedColumn(boolean flush) throws Throwable
    {
        createTable("CREATE TABLE %s (k int, c int, a int, b int, PRIMARY KEY (k, c))");
        createView("CREATE MATERIALIZED VIEW %s AS " +
                   "SELECT k,c FROM %s WHERE k IS NOT NULL AND c IS NOT NULL PRIMARY KEY (k,c)");
        Keyspace ks = Keyspace.open(keyspace());
        ks.getColumnFamilyStore(currentView()).disableAutoCompaction();

        updateView("UPDATE %s USING TIMESTAMP 10 SET b=1 WHERE k=1 AND c=1");
        if (flush)
            Util.flush(ks);
        assertRows(execute("SELECT * from %s"), row(1, 1, null, 1));
        assertRows(executeView("SELECT * FROM %s"), row(1, 1));
        updateView("DELETE b FROM %s USING TIMESTAMP 11 WHERE k=1 AND c=1");
        if (flush)
            Util.flush(ks);
        assertEmpty(execute("SELECT * from %s"));
        assertEmpty(executeView("SELECT * FROM %s"));
        updateView("UPDATE %s USING TIMESTAMP 1 SET a=1 WHERE k=1 AND c=1");
        if (flush)
            Util.flush(ks);
        assertRows(execute("SELECT * from %s"), row(1, 1, 1, null));
        assertRows(executeView("SELECT * FROM %s"), row(1, 1));

        execute("truncate %s;");

        // removal generated by unselected column should not shadow PK update with smaller timestamp
        updateViewWithFlush("UPDATE %s USING TIMESTAMP 18 SET a=1 WHERE k=1 AND c=1", flush);
        assertRows(execute("SELECT * from %s"), row(1, 1, 1, null));
        assertRows(executeView("SELECT * FROM %s"), row(1, 1));

        updateViewWithFlush("UPDATE %s USING TIMESTAMP 20 SET a=null WHERE k=1 AND c=1", flush);
        assertRows(execute("SELECT * from %s"));
        assertRows(executeView("SELECT * FROM %s"));

        updateViewWithFlush("INSERT INTO %s(k,c) VALUES(1,1) USING TIMESTAMP 15", flush);
        assertRows(execute("SELECT * from %s"), row(1, 1, null, null));
        assertRows(executeView("SELECT * FROM %s"), row(1, 1));
    }

    @Test
    public void testPartialDeleteSelectedColumnWithFlush() throws Throwable
    {
        testPartialDeleteSelectedColumn(true);
    }

    @Test
    public void testPartialDeleteSelectedColumnWithoutFlush() throws Throwable
    {
        testPartialDeleteSelectedColumn(false);
    }

    private void testPartialDeleteSelectedColumn(boolean flush) throws Throwable
    {
        createTable("CREATE TABLE %s (k int, c int, a int, b int, e int, f int, PRIMARY KEY (k, c))");
        createView("CREATE MATERIALIZED VIEW %s AS SELECT a, b, c, k FROM %s " +
                     "WHERE k IS NOT NULL AND c IS NOT NULL PRIMARY KEY (k,c)");
        Keyspace ks = Keyspace.open(keyspace());
        ks.getColumnFamilyStore(currentView()).disableAutoCompaction();

        updateViewWithFlush("UPDATE %s USING TIMESTAMP 10 SET b=1 WHERE k=1 AND c=1", flush);
        assertRows(execute("SELECT * from %s"), row(1, 1, null, 1, null, null));
        assertRows(executeView("SELECT * from %s"), row(1, 1, null, 1));

        updateViewWithFlush("DELETE b FROM %s USING TIMESTAMP 11 WHERE k=1 AND c=1", flush);
        assertEmpty(execute("SELECT * from %s"));
        assertEmpty(executeView("SELECT * from %s"));

        updateViewWithFlush("UPDATE %s USING TIMESTAMP 1 SET a=1 WHERE k=1 AND c=1", flush);
        assertRows(execute("SELECT * from %s"), row(1, 1, 1, null, null, null));
        assertRows(executeView("SELECT * from %s"), row(1, 1, 1, null));

        updateViewWithFlush("DELETE a FROM %s USING TIMESTAMP 1 WHERE k=1 AND c=1", flush);
        assertEmpty(execute("SELECT * from %s"));
        assertEmpty(executeView("SELECT * from %s"));

        // view livenessInfo should not be affected by selected column ts or tb
        updateViewWithFlush("INSERT INTO %s(k,c) VALUES(1,1) USING TIMESTAMP 0", flush);
        assertRows(execute("SELECT * from %s"), row(1, 1, null, null, null, null));
        assertRows(executeView("SELECT * from %s"), row(1, 1, null, null));

        updateViewWithFlush("UPDATE %s USING TIMESTAMP 12 SET b=1 WHERE k=1 AND c=1", flush);
        assertRows(execute("SELECT * from %s"), row(1, 1, null, 1, null, null));
        assertRows(executeView("SELECT * from %s"), row(1, 1, null, 1));

        updateViewWithFlush("DELETE b FROM %s USING TIMESTAMP 13 WHERE k=1 AND c=1", flush);
        assertRows(execute("SELECT * from %s"), row(1, 1, null, null, null, null));
        assertRows(executeView("SELECT * from %s"), row(1, 1, null, null));

        updateViewWithFlush("DELETE FROM %s USING TIMESTAMP 14 WHERE k=1 AND c=1", flush);
        assertEmpty(execute("SELECT * from %s"));
        assertEmpty(executeView("SELECT * from %s"));

        updateViewWithFlush("INSERT INTO %s(k,c) VALUES(1,1) USING TIMESTAMP 15", flush);
        assertRows(execute("SELECT * from %s"), row(1, 1, null, null, null, null));
        assertRows(executeView("SELECT * from %s"), row(1, 1, null, null));

        updateViewWithFlush("UPDATE %s USING TTL 3 SET b=1 WHERE k=1 AND c=1", flush);
        assertRows(execute("SELECT * from %s"), row(1, 1, null, 1, null, null));
        assertRows(executeView("SELECT * from %s"), row(1, 1, null, 1));

        TimeUnit.SECONDS.sleep(4);

        assertRows(execute("SELECT * from %s"), row(1, 1, null, null, null, null));
        assertRows(executeView("SELECT * from %s"), row(1, 1, null, null));

        updateViewWithFlush("DELETE FROM %s USING TIMESTAMP 15 WHERE k=1 AND c=1", flush);
        assertEmpty(execute("SELECT * from %s"));
        assertEmpty(executeView("SELECT * from %s"));

        execute("truncate %s;");

        // removal generated by unselected column should not shadow selected column with smaller timestamp
        updateViewWithFlush("UPDATE %s USING TIMESTAMP 18 SET e=1 WHERE k=1 AND c=1", flush);
        assertRows(execute("SELECT * from %s"), row(1, 1, null, null, 1, null));
        assertRows(executeView("SELECT * from %s"), row(1, 1, null, null));

        updateViewWithFlush("UPDATE %s USING TIMESTAMP 18 SET e=null WHERE k=1 AND c=1", flush);
        assertRows(execute("SELECT * from %s"));
        assertRows(executeView("SELECT * from %s"));

        updateViewWithFlush("UPDATE %s USING TIMESTAMP 16 SET a=1 WHERE k=1 AND c=1", flush);
        assertRows(execute("SELECT * from %s"), row(1, 1, 1, null, null, null));
        assertRows(executeView("SELECT * from %s"), row(1, 1, 1, null));
    }

    @Test
    public void testRangeDeletionWithFlush() throws Throwable
    {
        testRangeDeletion(true);
    }

    @Test
    public void testRangeDeletionWithoutFlush() throws Throwable
    {
        testRangeDeletion(false);
    }

    private void testRangeDeletion(boolean flush) throws Throwable
    {
        // for partition range deletion, need to know that existing row is shadowed instead of not existed.
        createTable("CREATE TABLE %s (a int, b int, c int, d int, PRIMARY KEY (a))");

        createView("CREATE MATERIALIZED VIEW %s AS SELECT * FROM %s " +
                     "WHERE a IS NOT NULL AND b IS NOT NULL PRIMARY KEY (a, b)");

        Keyspace ks = Keyspace.open(keyspace());
        ks.getColumnFamilyStore(currentView()).disableAutoCompaction();

        execute("INSERT INTO %s (a, b, c, d) VALUES (?, ?, ?, ?) using timestamp 0", 1, 1, 1, 1);
        if (flush)
            Util.flush(ks);

        assertRowsIgnoringOrder(executeView("SELECT * FROM %s"), row(1, 1, 1, 1));

        // remove view row
        updateView("UPDATE %s using timestamp 1 set b = null WHERE a=1");
        if (flush)
            Util.flush(ks);

        assertRowsIgnoringOrder(executeView("SELECT * FROM %s"));
        // remove base row, no view updated generated.
        updateView("DELETE FROM %s using timestamp 2 where a=1");
        if (flush)
            Util.flush(ks);

        assertRowsIgnoringOrder(executeView("SELECT * FROM %s"));

        // restor view row with b,c column. d is still tombstone
        updateView("UPDATE %s using timestamp 3 set b = 1,c = 1 where a=1"); // upsert
        if (flush)
            Util.flush(ks);

        assertRowsIgnoringOrder(executeView("SELECT * FROM %s"), row(1, 1, 1, null));
    }
}
