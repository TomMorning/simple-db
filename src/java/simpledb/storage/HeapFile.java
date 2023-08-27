package simpledb.storage;

import simpledb.common.Database;
import simpledb.common.DbException;
import simpledb.common.Debug;
import simpledb.common.Permissions;
import simpledb.transaction.TransactionAbortedException;
import simpledb.transaction.TransactionId;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 *
 * @author Sam Madden
 * @see HeapPage#HeapPage
 */
public class HeapFile implements DbFile {
    private final File f;
    private final TupleDesc td;

    /**
     * Constructs a heap file backed by the specified file.
     *
     * @param f the file that stores the on-disk backing store for this heap
     *          file.
     */
    public HeapFile(File f, TupleDesc td) {
        // TODO: some code goes here
        this.f = f;
        this.td = td;
    }

    /**
     * Returns the File backing this HeapFile on disk.
     *
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        // TODO: some code goes here
        return f;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere to ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     *
     * @return an ID uniquely identifying this HeapFile.
     */
    public int getId() {
        // TODO: some code goes here
        return f.getAbsoluteFile().hashCode();
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     *
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        // TODO: some code goes here
        return td;
    }

    // see DbFile.java for javadocs
    public Page readPage(PageId pid) {
        // TODO: some code goes here
        int pageSize = BufferPool.getPageSize();
        byte[] pageData = new byte[pageSize];
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            raf.seek((long) pid.getPageNumber() * pageSize);
            raf.readFully(pageData);
            raf.close();
            return new HeapPage(new HeapPageId(pid.getTableId(), pid.getPageNumber()), pageData);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read page from file.");
        }
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        // TODO: some code goes here
        // not necessary for lab1
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            raf.seek(page.getId().getPageNumber() * BufferPool.getPageSize());
            raf.write(page.getPageData());
        }
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        // TODO: some code goes here
        return (int) Math.ceil((double) f.length() / BufferPool.getPageSize());
    }

    // see DbFile.java for javadocs
    public List<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // TODO: some code goes here
        // not necessary for lab1
        // Check if there is any existing page with free slots
        List<Page> affectedPages = new ArrayList<>();
        for (int i = 0; i < numPages(); i++) {
            HeapPageId pid = new HeapPageId(getId(), i);
            HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid, pid, Permissions.READ_WRITE);
            if (page.getNumUnusedSlots() > 0) {
                page.insertTuple(t);
                affectedPages.add(page);
                return affectedPages;
            }
        }

        // No existing page with free slots, create a new page
        HeapPageId pid = new HeapPageId(getId(), numPages());
        HeapPage newPage = new HeapPage(pid, HeapPage.createEmptyPageData());
        newPage.insertTuple(t);
        writePage(newPage);
        affectedPages.add(newPage);
        return affectedPages;
    }

    // see DbFile.java for javadocs
    public List<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        // TODO: some code goes here
        // not necessary for lab1
        List<Page> affectedPages = new ArrayList<>();
        HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid, t.getRecordId().getPageId(), Permissions.READ_WRITE);
        page.deleteTuple(t);
        affectedPages.add(page);
        return affectedPages;
    }

    // see DbFile.java for javadocs
    public DbFileIterator iterator(TransactionId tid) {
        // TODO: some code goes here
        return new DbFileIterator() {
            private int currentPage = -1;
            private Iterator<Tuple> tupleIterator;

            @Override
            public void open() throws DbException, TransactionAbortedException {
                currentPage = 0;
                loadPage();
            }

            private void loadPage() throws DbException, TransactionAbortedException {
                if (currentPage >= 0 && currentPage < numPages()) {
                    HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid, new HeapPageId(getId(), currentPage), Permissions.READ_ONLY);
                    tupleIterator = page.iterator();
                } else {
                    tupleIterator = null;
                }
            }

            @Override
            public boolean hasNext() throws DbException, TransactionAbortedException {
                if (tupleIterator == null) return false;

                if (tupleIterator.hasNext()) return true;

                while (currentPage < numPages() - 1) {
                    currentPage++;
                    loadPage();
                    if (tupleIterator.hasNext()) return true;
                }

                return false;
            }

            @Override
            public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
                if (hasNext()) {
                    return tupleIterator.next();
                }
                throw new NoSuchElementException();
            }

            @Override
            public void rewind() throws DbException, TransactionAbortedException {
                close();
                open();
            }

            @Override
            public void close() {
                currentPage = -1;
                tupleIterator = null;
            }
        };
    }

}

