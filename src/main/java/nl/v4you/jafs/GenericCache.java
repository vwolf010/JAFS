package nl.v4you.jafs;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class GenericCache<K, V> {

    private class GenericCacheEntry {
        K key;
        V value;
        GenericCacheEntry l;
        GenericCacheEntry r;
    }

    private int cacheMaxSize = 100; // 3 is the minimum size!

    private Map<K, GenericCacheEntry> cache = new HashMap<>();
    private GenericCacheEntry mostLeft;
    private GenericCacheEntry mostRight;

    GenericCache(int size) throws JafsException {
        mostLeft = null;
        mostRight = null;
        if (size<3) {
            throw new JafsException("Cache size minimum 3");
        }
        cacheMaxSize = size;
    }

    void add(K key, V value) {
        if (cache.size()>= cacheMaxSize) {
            // Cache too big? Evict (=delete) the oldest entry
            GenericCacheEntry tmp = mostLeft;
            mostLeft = mostLeft.r;
            mostLeft.l = null;
            cache.remove(tmp.key);
        }

        // Create new entry
        GenericCacheEntry ce = new GenericCacheEntry();
        ce.key = key;
        ce.value = value;
        cache.put(key, ce);

        addEntry(ce);
    }

    V get(K key) {
        GenericCacheEntry ce = null;

        // Check if this block is already in cache
        ce = cache.get(key);
        if (ce==null) {
            return null;
        }

        removeEntry(ce);
        addEntry(ce);

        return ce.value;
    }

    void remove(K key) {
        GenericCacheEntry ce = cache.get(key);
        if (ce!=null) {
            removeEntry(cache.get(key));
            cache.remove(ce.key);
        }
    }

    int size() {
        return cache.size();
    }

    private void addEntry(GenericCacheEntry ce) {
        // First entry? Set mostleft
        if (mostLeft==null) mostLeft=ce;

        // Update access list (add to the right)
        ce.r = null;
        ce.l = mostRight;
        if (mostRight!=null) mostRight.r = ce;
        mostRight = ce;
    }

    private void removeEntry(GenericCacheEntry ce) {
        if (ce!=null) {
            if (ce == mostLeft) mostLeft = ce.r;
            if (mostLeft != null) mostLeft.l = null;

            if (ce == mostRight) mostRight = ce.l;
            if (mostRight != null) mostRight.r = null;

            if (ce.l != null) ce.l.r = ce.r;
            if (ce.r != null) ce.r.l = ce.l;
        }
    }
}
