package nl.v4you.jafs;

public class JafsDirEntryCache {

    private GenericCache<DxHackHash, JafsDirEntry> gcache;

    DxHackHash hs = new DxHackHash(null);

    JafsDirEntryCache(int size) throws JafsException {
        gcache = new GenericCache<>(size);
    }

    void add(String dirName, JafsDirEntry entry) throws JafsException {
        if (gcache.get(hs.set(dirName.getBytes(Util.UTF8)))!=null) {
            throw new JafsException("directory "+dirName+" already in cache");
        }
        gcache.add(new DxHackHash(dirName.getBytes(Util.UTF8)), entry);
    }

    JafsDirEntry get(String dirName) {
        return gcache.get(hs.set(dirName.getBytes(Util.UTF8)));
    }

    void remove(String dirName) {
        gcache.remove(hs.set(dirName.getBytes(Util.UTF8)));
    }
}
