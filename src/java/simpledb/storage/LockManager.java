package simpledb.storage;




import simpledb.common.Permissions;
import simpledb.transaction.TransactionId;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LockManager {
    private final Map<PageId, Set<TransactionId>> sharedLocks = new HashMap<>();
    private final Map<PageId, TransactionId> exclusiveLocks = new HashMap<>();
    private final Map<TransactionId, Set<PageId>> transactionPages = new HashMap<>();
    private final Map<PageId, Object> lockConditions = new HashMap<>();

    public synchronized void releaseAllLocks(TransactionId tid) {
        // 获取该事务持有的所有页面的副本，以避免在迭代时修改原始集合
        Set<PageId> pagesToRelease = new HashSet<>();
        if (transactionPages.containsKey(tid)) {
            pagesToRelease.addAll(transactionPages.get(tid));
        }

        // 遍历副本集合并释放每个页面的锁
        for (PageId pid : pagesToRelease) {
            releaseLock(tid, pid);
        }

        // 从事务的页面映射中移除该事务
        transactionPages.remove(tid);
    }

    public synchronized boolean acquireLock(TransactionId tid, PageId pid, Permissions perm) {
        lockConditions.putIfAbsent(pid, new Object());
        Object lockCondition = lockConditions.get(pid);
        while (true) {
            boolean hasExclusiveLock = exclusiveLocks.containsKey(pid);
            Set<TransactionId> sharedTids = sharedLocks.getOrDefault(pid, new HashSet<>());

            if (perm == Permissions.READ_ONLY) {
                if (!hasExclusiveLock || sharedTids.contains(tid)) {
                    // 添加共享锁
                    sharedTids.add(tid);
                    sharedLocks.put(pid, sharedTids);

                    // 更新 transactionPages 映射
                    addPageToTransaction(tid, pid);
                    return true;
                }
            } else if (perm == Permissions.READ_WRITE) {
                if ((!hasExclusiveLock && sharedTids.isEmpty()) || (sharedTids.size() == 1 && sharedTids.contains(tid))) {
                    // 添加或升级为排他锁
                    exclusiveLocks.put(pid, tid);
                    sharedLocks.remove(pid); // 清除任何现有的共享锁

                    // 更新 transactionPages 映射
                    addPageToTransaction(tid, pid);
                    return true;
                }
            }
            // 如果需要排他锁，但当前有其他共享锁，则等待
            waitForLock(lockCondition);
        }
    }

    private void waitForLock(Object lockCondition) {
        synchronized (lockCondition) {
            try {
                lockCondition.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void addPageToTransaction(TransactionId tid, PageId pid) {
        transactionPages.putIfAbsent(tid, new HashSet<>());
        transactionPages.get(tid).add(pid);
    }

    public synchronized void releaseLock(TransactionId tid, PageId pid) {
        Set<TransactionId> sharedTids = sharedLocks.getOrDefault(pid, new HashSet<>());
        if (sharedTids.contains(tid)) {
            sharedTids.remove(tid);
            if (sharedTids.isEmpty()) {
                sharedLocks.remove(pid);
            } else {
                sharedLocks.put(pid, sharedTids);
            }
        }

        if (exclusiveLocks.get(pid) != null && exclusiveLocks.get(pid).equals(tid)) {
            exclusiveLocks.remove(pid);
        }

        // 获取锁条件对象并通知所有等待的线程
        Object lockCondition = lockConditions.get(pid);
        if (lockCondition != null) {
            synchronized (lockCondition) {
                lockCondition.notifyAll();
            }
        }
    }

    public synchronized boolean holdsLock(TransactionId tid, PageId pid) {
        return exclusiveLocks.get(pid) == tid || sharedLocks.getOrDefault(pid, new HashSet<>()).contains(tid);
    }
}

