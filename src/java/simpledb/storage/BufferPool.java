package simpledb.storage;

import simpledb.common.Database;
import simpledb.common.DbException;
import simpledb.common.DeadlockException;
import simpledb.common.Permissions;
import simpledb.transaction.TransactionAbortedException;
import simpledb.transaction.TransactionId;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * BufferPool manages the reading and writing of pages into memory from
 * disk. Access methods call into it to retrieve pages, and it fetches
 * pages from the appropriate location.
 * <p>
 * The BufferPool is also responsible for locking;  when a transaction fetches
 * a page, BufferPool checks that the transaction has the appropriate
 * locks to read/write the page.
 *
 * @Threadsafe, all fields are final
 */
public class BufferPool {
    /**
     * Bytes per page, including header.
     */
    private static final int DEFAULT_PAGE_SIZE = 4096;

    private static int pageSize = DEFAULT_PAGE_SIZE;
    private final LinkedHashMap<PageId, Page> pageMap;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final int maxPages;
    private final LockManager lockManager = new LockManager();

    /**
     * Default number of pages passed to the constructor. This is used by
     * other classes. BufferPool should use the numPages argument to the
     * constructor instead.
     */
    public static final int DEFAULT_PAGES = 50;

    /**
     * Creates a BufferPool that caches up to numPages pages.
     *
     * @param numPages maximum number of pages in this buffer pool.
     */
    public BufferPool(int numPages) {
        // TODO: some code goes here
        pageMap = new LinkedHashMap<>();
        maxPages = numPages;
    }

    public static int getPageSize() {
        return pageSize;
    }

    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void setPageSize(int pageSize) {
        BufferPool.pageSize = pageSize;
    }

    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void resetPageSize() {
        BufferPool.pageSize = DEFAULT_PAGE_SIZE;
    }

    /**
     * Retrieve the specified page with the associated permissions.
     * Will acquire a lock and may block if that lock is held by another
     * transaction.
     * <p>
     * The retrieved page should be looked up in the buffer pool.  If it
     * is present, it should be returned.  If it is not present, it should
     * be added to the buffer pool and returned.  If there is insufficient
     * space in the buffer pool, a page should be evicted and the new page
     * should be added in its place.
     *
     * @param tid  the ID of the transaction requesting the page
     * @param pid  the ID of the requested page
     * @param perm the requested permissions on the page
     */
    public Page getPage(TransactionId tid, PageId pid, Permissions perm)
            throws TransactionAbortedException, DbException {
        if (pageMap.containsKey(pid) && lockManager.holdsLock(tid, pid) && pageMap.get(pid).isDirty() == null) {
            lockManager.releaseLock(tid, pid);
        }
        boolean lockAcquired = lockManager.acquireLock(tid, pid, perm);
        if (!lockAcquired) {
            throw new TransactionAbortedException();
        }

        try {
            Page cachedPage = pageMap.get(pid);
            if (cachedPage != null) {
                return cachedPage;
            }

            if (pageMap.size() >= maxPages) {
                evictPage();
            }

            DbFile dbFile = Database.getCatalog().getDatabaseFile(pid.getTableId());
            Page page = dbFile.readPage(pid);
            pageMap.put(pid, page);
            return page;
        } finally {
            // Do not release lock here; it should be held until transaction completion
        }
    }



    /**
     * Releases the lock on a page.
     * Calling this is very risky, and may result in wrong behavior. Think hard
     * about who needs to call this and why, and why they can run the risk of
     * calling it.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param pid the ID of the page to unlock
     */
    public void unsafeReleasePage(TransactionId tid, PageId pid) {
        // TODO: some code goes here
        // not necessary for lab1|lab2
        lockManager.releaseLock(tid, pid);
    }

    /**
     * Release all locks associated with a given transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     */
    public void transactionComplete(TransactionId tid) {
        // TODO: some code goes here
        // not necessary for lab1|lab2
    }

    /**
     * Return true if the specified transaction has a lock on the specified page
     */
    public boolean holdsLock(TransactionId tid, PageId pid) {
        // TODO: some code goes here
        // not necessary for lab1|lab2
        return lockManager.holdsLock(tid, pid);
    }

    /**
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
     *
     * @param tid    the ID of the transaction requesting the unlock
     * @param commit a flag indicating whether we should commit or abort
     */
    public void transactionComplete(TransactionId tid, boolean commit) {
        // TODO: some code goes here
        // not necessary for lab1|lab2
        if (commit) {
            // 提交事务：将所有脏页刷新到磁盘
            try {
                flushPages(tid);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // 中止事务：回滚所有更改
//            rollback(tid);
        }
//
//        // 释放该事务持有的所有锁
        lockManager.releaseAllLocks(tid);
    }

    /**
     * Add a tuple to the specified table on behalf of transaction tid.  Will
     * acquire a write lock on the page the tuple is added to and any other
     * pages that are updated (Lock acquisition is not needed for lab2).
     * May block if the lock(s) cannot be acquired.
     * <p>
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have
     * been dirtied to the cache (replacing any existing versions of those pages) so
     * that future requests see up-to-date pages.
     *
     * @param tid     the transaction adding the tuple
     * @param tableId the table to add the tuple to
     * @param t       the tuple to add
     */
    public void insertTuple(TransactionId tid, int tableId, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // TODO: some code goes here
        // not necessary for lab1
        DbFile dbFile = Database.getCatalog().getDatabaseFile(tableId);
        List<Page> dirtiedPages = dbFile.insertTuple(tid, t);

        lock.writeLock().lock();
        try {
            for (Page page : dirtiedPages) {
                page.markDirty(true, tid);
                // Update the page in the buffer pool
                if (pageMap.containsKey(page.getId())) {
                    pageMap.put(page.getId(), page);
                } else {
                    if (pageMap.size() >= maxPages) {
                        evictPage();
                    }
                    pageMap.put(page.getId(), page);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Remove the specified tuple from the buffer pool.
     * Will acquire a write lock on the page the tuple is removed from and any
     * other pages that are updated. May block if the lock(s) cannot be acquired.
     * <p>
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have
     * been dirtied to the cache (replacing any existing versions of those pages) so
     * that future requests see up-to-date pages.
     *
     * @param tid the transaction deleting the tuple.
     * @param t   the tuple to delete
     */
    public void deleteTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // TODO: some code goes here
        // not necessary for lab1
        int tableId = t.getRecordId().getPageId().getTableId();
        DbFile dbFile = Database.getCatalog().getDatabaseFile(tableId);
        List<Page> dirtiedPages = dbFile.deleteTuple(tid, t);

        lock.writeLock().lock();
        try {
            for (Page page : dirtiedPages) {
                page.markDirty(true, tid);
                // Update the page in the buffer pool
                if (pageMap.containsKey(page.getId())) {
                    pageMap.put(page.getId(), page);
                } else {
                    if (pageMap.size() >= maxPages) {
                        evictPage();
                    }
                    pageMap.put(page.getId(), page);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Flush all dirty pages to disk.
     * NB: Be careful using this routine -- it writes dirty data to disk so will
     * break simpledb if running in NO STEAL mode.
     */
    public synchronized void flushAllPages() throws IOException {
        // TODO: some code goes here
        // not necessary for lab1
        lock.writeLock().lock();
        try {
            for (Map.Entry<PageId, Page> entry : pageMap.entrySet()) {
                PageId pid = entry.getKey();
                Page page = entry.getValue();
                if (page.isDirty() != null) {
                    flushPage(pid);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Remove the specific page id from the buffer pool.
     * Needed by the recovery manager to ensure that the
     * buffer pool doesn't keep a rolled back page in its
     * cache.
     * <p>
     * Also used by B+ tree files to ensure that deleted pages
     * are removed from the cache so they can be reused safely
     */
    public synchronized void removePage(PageId pid) {
        // TODO: some code goes here
        // not necessary for lab1
        lock.writeLock().lock();
        try {
            pageMap.remove(pid);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Flushes a certain page to disk
     *
     * @param pid an ID indicating the page to flush
     */
    private synchronized void flushPage(PageId pid) throws IOException {
        // TODO: some code goes here
        // not necessary for lab1
        lock.writeLock().lock();
        try {
            Page page = pageMap.get(pid);
            TransactionId dirtyingId = page.isDirty();
            if (dirtyingId != null) {
                DbFile dbFile = Database.getCatalog().getDatabaseFile(pid.getTableId());
                dbFile.writePage(page);
                page.markDirty(false, null);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Write all pages of the specified transaction to disk.
     */
    public synchronized void flushPages(TransactionId tid) throws IOException {
        // TODO: some code goes here
        // not necessary for lab1|lab2
        for (PageId pid : pageMap.keySet()) {
            Page page = pageMap.get(pid);
            // 检查页面是否为脏且由指定的事务修改
            if (page.isDirty() != null && page.isDirty().equals(tid)) {
                flushPage(pid);  // 利用已实现的 flushPage 方法写入页面
                page.markDirty(false, null);  // 清除脏标记
            }
        }
    }

    /**
     * Discards a page from the buffer pool.
     * Flushes the page to disk to ensure dirty pages are updated on disk.
     */
    private synchronized void evictPage() throws DbException {
        // TODO: some code goes here
        // not necessary for lab1
        lock.writeLock().lock();
        try {
            PageId evictCandidate = null;
            for (PageId pid : pageMap.keySet()) {
                evictCandidate = pid;
                break; // Just taking the first one for now. You may implement LRU or another strategy.
            }

            if (evictCandidate == null) {
                throw new DbException("No page to evict!");
            }

            flushPage(evictCandidate); // Flush to disk before eviction
            pageMap.remove(evictCandidate);
        } catch (IOException e) {
            throw new RuntimeException("I/O exception when evicting page");
        } finally {
            lock.writeLock().unlock();
        }
    }

}
