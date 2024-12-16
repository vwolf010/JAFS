package nl.v4you.jafs;

import nl.v4you.hash.OneAtATimeHash;

import java.nio.charset.StandardCharsets;

class ZDirEntryCache {

    private ZLRUCache<OneAtATimeHash, ZDirEntry> gcache;

    OneAtATimeHash hs = new OneAtATimeHash(null);

    ZDirEntryCache(int size) throws JafsException {
        gcache = new ZLRUCache<>(size);
    }

    void add(String dirName, ZDirEntry entry) throws JafsException {
        if (gcache.get(hs.set(dirName.getBytes(StandardCharsets.UTF_8))) != null) {
            throw new JafsException("directory " + dirName + " already in cache");
        }
        gcache.add(hs.clone(), entry);
    }

    ZDirEntry get(String dirName) {
        return gcache.get(hs.set(dirName.getBytes(StandardCharsets.UTF_8)));
    }

    void remove(String dirName) {
        gcache.remove(hs.set(dirName.getBytes(StandardCharsets.UTF_8)));
    }

    String stats() {
        return gcache.stats();
    }
}
