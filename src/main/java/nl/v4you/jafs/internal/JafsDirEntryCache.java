package nl.v4you.jafs.internal;

import nl.v4you.hash.OneAtATimeHash;
import nl.v4you.jafs.JafsException;

public class JafsDirEntryCache {

    private LRUCache<OneAtATimeHash, JafsDirEntry> gcache;

    OneAtATimeHash hs = new OneAtATimeHash(null);

    public JafsDirEntryCache(int size) throws JafsException {
        gcache = new LRUCache<>(size);
    }

    public void add(String dirName, JafsDirEntry entry) throws JafsException {
        if (gcache.get(hs.set(dirName.getBytes(Util.UTF8)))!=null) {
            throw new JafsException("directory "+dirName+" already in cache");
        }
        gcache.add(hs.clone(), entry);
    }

    public JafsDirEntry get(String dirName) {
        return gcache.get(hs.set(dirName.getBytes(Util.UTF8)));
    }

    void remove(String dirName) {
        gcache.remove(hs.set(dirName.getBytes(Util.UTF8)));
    }

    public String stats() {
        return gcache.stats();
    }
}
