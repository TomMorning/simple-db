package simpledb.execution;

import simpledb.common.DbException;
import simpledb.common.Type;
import simpledb.storage.Field;
import simpledb.storage.IntField;
import simpledb.storage.Tuple;
import simpledb.storage.TupleDesc;
import simpledb.transaction.TransactionAbortedException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Knows how to compute some aggregate over a set of IntFields.
 */
public class IntegerAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;
    private int gbfield;
    private Type gbfieldtype;
    private int afield;
    private Op what;

    private Map<Field, Integer> countMap = new HashMap<>();
    private Map<Field, Integer> valueMap = new HashMap<>();

    /**
     * Aggregate constructor
     *
     * @param gbfield     the 0-based index of the group-by field in the tuple, or
     *                    NO_GROUPING if there is no grouping
     * @param gbfieldtype the type of the group by field (e.g., Type.INT_TYPE), or null
     *                    if there is no grouping
     * @param afield      the 0-based index of the aggregate field in the tuple
     * @param what        the aggregation operator
     */

    public IntegerAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        // TODO: some code goes here
        this.gbfield = gbfield;
        this.gbfieldtype = gbfieldtype;
        this.afield = afield;
        this.what = what;
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the
     * constructor
     *
     * @param tup the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        // TODO: some code goes here
        Field groupField = gbfield == NO_GROUPING ? null : tup.getField(gbfield);
        int value = ((IntField) tup.getField(afield)).getValue();

        int currentValue = valueMap.getOrDefault(groupField, (what == Op.MIN ? Integer.MAX_VALUE : 0));
        int currentCount = countMap.getOrDefault(groupField, 0);

        switch (what) {
            case SUM:
                currentValue += value;
                break;
            case MIN:
                currentValue = Math.min(currentValue, value);
                break;
            case MAX:
                currentValue = Math.max(currentValue, value);
                break;
            case AVG:
                currentValue += value;
                currentCount += 1;
                countMap.put(groupField, currentCount);
                break;
            default:
                break;
        }
        valueMap.put(groupField, currentValue);
    }

    /**
     * Create a OpIterator over group aggregate results.
     *
     * @return a OpIterator whose tuples are the pair (groupVal, aggregateVal)
     *         if using group, or a single (aggregateVal) if no grouping. The
     *         aggregateVal is determined by the type of aggregate specified in
     *         the constructor.
     */
    public OpIterator iterator() {
        // TODO: some code goes here
        return new OpIterator() {
            private Iterator<Map.Entry<Field, Integer>> entries = valueMap.entrySet().iterator();
            private Map.Entry<Field, Integer> currentEntry = null;
            private boolean isOpen = false;
            @Override
            public void open() throws DbException, TransactionAbortedException {
                entries = valueMap.entrySet().iterator();
                isOpen = true;
            }

            @Override
            public boolean hasNext() throws DbException, TransactionAbortedException {
                return isOpen && entries.hasNext();
            }

            @Override
            public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                currentEntry = entries.next();
                int value = currentEntry.getValue();
                if (what == Op.AVG) {
                    value /= countMap.get(currentEntry.getKey());
                }
                Tuple tuple = new Tuple(getTupleDesc());
                if (gbfield == NO_GROUPING) {
                    tuple.setField(0, new IntField(value));
                } else {
                    tuple.setField(0, currentEntry.getKey());
                    tuple.setField(1, new IntField(value));
                }
                return tuple;
            }

            @Override
            public void rewind() throws DbException, TransactionAbortedException {
                entries = valueMap.entrySet().iterator();
            }

            @Override
            public TupleDesc getTupleDesc() {
                if (gbfield == NO_GROUPING) {
                    return new TupleDesc(new Type[]{Type.INT_TYPE});
                } else {
                    return new TupleDesc(new Type[]{gbfieldtype, Type.INT_TYPE});
                }
            }

            @Override
            public void close() {
                isOpen = false;
            }
        };
    }

}
