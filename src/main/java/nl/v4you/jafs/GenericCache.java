package nl.v4you.jafs;

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

    private long cntAdded = 0;
    private long cntEvicted = 0;
    private long cntHit = 0;
    private long cntMiss = 0;
    private long cntRemoved = 0;

    GenericCache(int size) {
        mostLeft = null;
        mostRight = null;
        if (size<3) {
            throw new IllegalStateException("Cache size minimum is 3");
        }
        cacheMaxSize = size;
    }

    V add(K key, V value) {
        GenericCacheEntry ce = null;
        V evicted = null;

        if (cache.size()>= cacheMaxSize) {
            // Cache too big? Evict (=delete) the oldest entry
            GenericCacheEntry tmp = mostLeft;
            mostLeft = mostLeft.r;
            mostLeft.l = null;
            ce = cache.remove(tmp.key);
            evicted = ce.value;
            cntEvicted++;
        }

        // Create new entry
        if (ce==null) {
            ce = new GenericCacheEntry();
        }
        ce.key = key;
        ce.value = value;
        cache.put(key, ce);

        addEntry(ce);
        cntAdded++;

        return evicted;
    }

    V get(K key) {
        GenericCacheEntry ce;

        // Check if this block is already in cache
        ce = cache.get(key);
        if (ce==null) {
            cntMiss++;
            return null;
        }
        cntHit++;

        removeEntry(ce);
        addEntry(ce);

        return ce.value;
    }

    void remove(K key) {
        GenericCacheEntry ce = cache.get(key);
        if (ce!=null) {
            removeEntry(cache.get(key));
            cache.remove(ce.key);
            cntRemoved++;
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

    String stats() {
        StringBuilder sb = new StringBuilder();
        int used = (int) Math.round((cache.size()*100.0)/cacheMaxSize);
        sb.append("   size    : "+cache.size()+" ("+used+"%)\n");
        sb.append("   added   : "+cntAdded+"\n");
        sb.append("   evicted : "+cntEvicted+"\n");
        sb.append("   removed : "+cntRemoved+"\n");
        int hit = (int)Math.round((cntHit*100.0)/(cntHit+cntMiss));
        sb.append("   hit     : "+cntHit+" ("+hit+"%)\n");
        sb.append("   miss    : "+cntMiss+" ("+(100-hit)+"%)\n");
        return sb.toString();
    }
}
