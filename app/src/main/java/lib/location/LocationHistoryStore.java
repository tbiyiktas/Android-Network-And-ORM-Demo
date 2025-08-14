package lib.location;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/** Sabit kapasiteli, thread-safe ring buffer. */
public class LocationHistoryStore<T> {
    private final ArrayDeque<T> deque;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile int capacity;

    public LocationHistoryStore(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.deque = new ArrayDeque<>(this.capacity);
    }

    public void add(@NonNull T item) {
        lock.lock();
        try {
            if (deque.size() == capacity) {
                deque.removeFirst();
            }
            deque.addLast(item);
        } finally {
            lock.unlock();
        }
    }

    /** En yeni → en eski sıralı kopya döner. */
    @NonNull
    public List<T> snapshotNewestFirst() {
        lock.lock();
        try {
            ArrayList<T> list = new ArrayList<>(deque);
            Collections.reverse(list);
            return list;
        } finally {
            lock.unlock();
        }
    }

    public int capacity() { return capacity; }

    /** Çalışırken kapasite değişimi (fazlaysa baştan atar). */
    public void setCapacity(int newCapacity) {
        lock.lock();
        try {
            newCapacity = Math.max(1, newCapacity);
            if (newCapacity == capacity) return;
            while (deque.size() > newCapacity) {
                deque.removeFirst();
            }
            this.capacity = newCapacity;
        } finally {
            lock.unlock();
        }
    }
}
