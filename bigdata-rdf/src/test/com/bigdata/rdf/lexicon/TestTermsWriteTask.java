/**

Copyright (C) SYSTAP, LLC 2006-2011.  All rights reserved.

Contact:
     SYSTAP, LLC
     4501 Tower Road
     Greensboro, NC 27410
     licenses@bigdata.com

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 2 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
/*
 * Created on Jun 9, 2011
 */

package com.bigdata.rdf.lexicon;

import junit.framework.TestCase2;

import org.openrdf.model.Value;
import org.openrdf.model.impl.BNodeImpl;
import org.openrdf.model.impl.LiteralImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.model.vocabulary.RDF;

import com.bigdata.btree.BTree;
import com.bigdata.btree.IIndex;
import com.bigdata.btree.keys.IKeyBuilder;
import com.bigdata.rawstore.IRawStore;
import com.bigdata.rawstore.SimpleMemoryRawStore;
import com.bigdata.rdf.internal.IV;
import com.bigdata.rdf.internal.IVUtility;
import com.bigdata.rdf.internal.TermId;
import com.bigdata.rdf.model.BigdataValue;
import com.bigdata.rdf.model.BigdataValueFactory;
import com.bigdata.rdf.model.BigdataValueFactoryImpl;

/**
 * Test suite for the {@link TermsWriteTask}.
 * 
 * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan Thompson</a>
 * @version $Id$
 */
public class TestTermsWriteTask extends TestCase2 {

    /**
     * 
     */
    public TestTermsWriteTask() {
    }

    /**
     * @param name
     */
    public TestTermsWriteTask(String name) {
        super(name);
    }

    public void test_add_abc() {

        // The values that we will be testing with.
        final Value[] valuesIn = new Value[] {//
          
                new LiteralImpl("abc") //
                
        };

        doAddTermsTest(valuesIn, false/* toldBNodes */);
        
    }
    
    public void test_add_emptyLiteral() {

        // The values that we will be testing with.
        final Value[] valuesIn = new Value[] {//
          
                new LiteralImpl("") //
                
        };

        doAddTermsTest(valuesIn, false/* toldBNodes */);
        
    }
    
    public void test_add_various_toldBNodes() {

        // The values that we will be testing with.
        final Value[] valuesIn = new Value[] {//
          
                new LiteralImpl("abc"), //
                RDF.TYPE,//
                RDF.PROPERTY,//
                new LiteralImpl("test"),//
                new LiteralImpl("test", "en"),//
                new LiteralImpl("10", new URIImpl("http://www.w3.org/2001/XMLSchema#int")),
                new LiteralImpl("12", new URIImpl("http://www.w3.org/2001/XMLSchema#float")),
                new LiteralImpl("12.", new URIImpl("http://www.w3.org/2001/XMLSchema#float")),
                new LiteralImpl("12.0", new URIImpl("http://www.w3.org/2001/XMLSchema#float")),
                new LiteralImpl("12.00", new URIImpl("http://www.w3.org/2001/XMLSchema#float")),
                new BNodeImpl("a"),//
                new BNodeImpl("12"),//
        };

        doAddTermsTest(valuesIn, true/* toldBNodes */);
        
    }

    /**
     * Note: Blank nodes are NOT included in this test since the test helper is
     * not smart enough to verify the blank nodes will not be unified when using
     * standard bnode semantics. However, {@link TestTermsIndex} does verify
     * both standard and told bnode semantics.
     */
    public void test_add_various_standardBNodes() {

        /* The values that we will be testing with.
         */
        final Value[] valuesIn = new Value[] {//
          
                new LiteralImpl("abc"), //
                RDF.TYPE,//
                RDF.PROPERTY,//
                new LiteralImpl("test"),//
                new LiteralImpl("test", "en"),//
                new LiteralImpl("10", new URIImpl("http://www.w3.org/2001/XMLSchema#int")),
                new LiteralImpl("12", new URIImpl("http://www.w3.org/2001/XMLSchema#float")),
                new LiteralImpl("12.", new URIImpl("http://www.w3.org/2001/XMLSchema#float")),
                new LiteralImpl("12.0", new URIImpl("http://www.w3.org/2001/XMLSchema#float")),
                new LiteralImpl("12.00", new URIImpl("http://www.w3.org/2001/XMLSchema#float")),
//                new BNodeImpl("a"),//
//                new BNodeImpl("12"),//
        };

        doAddTermsTest(valuesIn, false/* toldBNodes */);
        
    }
    
    private void doAddTermsTest(final Value[] valuesIn, final boolean toldBNodes) {

        for(int i=0; i<valuesIn.length; i++) {
         
            final Value v = valuesIn[i];
            
            if(v == null)
                fail("Not expecting null inputs.");

            if(v instanceof BigdataValue)
                fail("Not expecting BigdataValue inputs.");
            
        }
        
        final TermsIndexHelper h = new TermsIndexHelper();

        final BigdataValueFactory vf = BigdataValueFactoryImpl
                .getInstance(getName());

        final IRawStore store = new SimpleMemoryRawStore();

        try {

            final BTree ndx = TestTermsIndex.createTermsIndex(store, getName());

            /*
             * Point test the TERMS index for each Value. It should not be
             * found.
             */
            {

                for (int i = 0; i < valuesIn.length; i++) {

                    final IV iv = getTermIV(valuesIn[i], h, vf, ndx);

                    assertNull(iv);

                }

            }

            /*
             * Batch test the TERMS index for each value. It should not be
             * found.
             */
            {

                /*
                 * Convert to BigdataValues so IVs will be assigned as a
                 * side-effect by the TermsWriteTask.
                 */
                final BigdataValue[] values = new BigdataValue[valuesIn.length];

                for (int i = 0; i < valuesIn.length; i++) {

                    values[i] = vf.asValue(valuesIn[i]);

                }

                /*
                 * Invoke the task in a read-only mode to unify with the
                 * existing entries in the TERMS index.
                 */
                final WriteTaskStats stats = addValues(vf, ndx,
                        true/* readOnly */, toldBNodes, values);

                for (int i = 0; i < valuesIn.length; i++) {

                    final BigdataValue value = values[i];

                    final IV iv = value.getIV();

                    if (iv != null)
                        fail("Not expecting IV for " + value);

                }

            }

            // Write the Value on the TERMS index, noting the assigned IVs.
            final IV[] expectedIVs = new IV[valuesIn.length];
            {

                /*
                 * Convert to BigdataValues so IVs will be assigned as a
                 * side-effect by the TermsWriteTask.
                 */
                final BigdataValue[] values = new BigdataValue[valuesIn.length];

                for (int i = 0; i < valuesIn.length; i++) {

                    values[i] = vf.asValue(valuesIn[i]);

                }

                /*
                 * Write on the TERMS index, unifying with the existing values
                 * in the index and assigning IVs to new values.
                 */
                final WriteTaskStats stats = addValues(vf, ndx,
                        false/* readOnly */, toldBNodes, values);

                for (int i = 0; i < valuesIn.length; i++) {

                    final BigdataValue value = values[i];

                    final IV iv = value.getIV();

                    assertNotNull(iv);

                    if (iv.isNullIV())
                        fail("Not expecting NullIV for " + value);

                    expectedIVs[i] = iv;

                }

            }

            /*
             * Batch test the TERMS index for each value. It should now be
             * found.
             */
            {

                /*
                 * Convert to BigdataValues so IVs will be assigned as a
                 * side-effect by the TermsWriteTask.
                 */
                final BigdataValue[] values = new BigdataValue[valuesIn.length];

                for (int i = 0; i < valuesIn.length; i++) {

                    values[i] = vf.asValue(valuesIn[i]);

                }

                /*
                 * Invoke the task in a read-only mode to unify with the
                 * existing entries in the TERMS index.
                 */
                final WriteTaskStats stats = addValues(vf, ndx,
                        true/* readOnly */, toldBNodes, values);

                for (int i = 0; i < valuesIn.length; i++) {

                    final BigdataValue value = values[i];

                    final IV expectedIV = expectedIVs[i];

                    final IV actualIV = value.getIV();

                    if (!expectedIV.equals(actualIV))
                        fail("Value=" + value + ": expected=" + expectedIV
                                + ", but actual=" + actualIV);

                }

            }

            /*
             * Point test the TERMS index for each Value. It should now be
             * found.
             */
            {

                for (int i = 0; i < valuesIn.length; i++) {

                    final Value value = valuesIn[i];
                    
                    final IV expectedIV = expectedIVs[i];

                    final IV actualIV = getTermIV(valuesIn[i], h, vf, ndx);

                    if (!expectedIV.equals(actualIV))
                        fail("Value=" + value + ": expected=" + expectedIV
                                + ", but actual=" + actualIV);

                }

            }

        } finally {

            store.destroy();

        }

    }

    /**
     * Test helper similar to {@link LexiconRelation#getIV(Value)} (point
     * lookup).
     * 
     * @param value
     * @param h
     * @param vf
     * @param ndx
     * @return
     * 
     *         TODO Replace with batch resolution using the
     *         {@link TermsWriteTask} since that is after all what we are trying
     *         to test!
     */
    private IV getTermIV(final Value value, final TermsIndexHelper h,
            final BigdataValueFactory vf, final IIndex ndx) {

        final IKeyBuilder keyBuilder = h.newKeyBuilder();

        final BigdataValue asValue = vf.asValue(value);

        final byte[] baseKey = h.makePrefixKey(keyBuilder.reset(), asValue);

        final byte[] val = vf.getValueSerializer().serialize(asValue);

        final byte[] key = h.resolveOrAddValue(ndx, true/* readOnly */,
                keyBuilder, baseKey, val);

        if (key == null) {

            // Not found.
            return null;

        }

        final TermId<?> iv = (TermId<?>) IVUtility.decode(key);

        return iv;

    }

    /**
     * Thin wrapper for the {@link TermsWriteTask} as invoked by the
     * {@link LexiconRelation}. This can be used to unify the {@link IV}s for
     * the caller's {@link BigdataValue} with those in the TERMS index and
     * (optionally) to assign new {@link IV}s (when read-only is
     * <code>false</code>).
     * 
     * @param vf
     * @param ndx
     * @param readOnly
     * @param toldBNodes
     * @param values
     * @return
     */
    private WriteTaskStats addValues(final BigdataValueFactory vf,
            final IIndex ndx, final boolean readOnly, final boolean toldBNodes,
            final BigdataValue[] values) {

        final WriteTaskStats stats = new WriteTaskStats();

        final TermsWriteTask task = new TermsWriteTask(ndx, vf, readOnly,
                toldBNodes, values.length, values, stats);

        try {
            task.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return stats;

    }

}
