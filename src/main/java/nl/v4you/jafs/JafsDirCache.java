package nl.v4you.jafs;

public class JafsDirCache {

    private GenericCache<String, JafsDirEntry> gcache;

    JafsDirCache(int size) throws JafsException {
        gcache = new GenericCache<>(size);
    }

    void add(String dirName, JafsDirEntry entry) throws JafsException {
        if (gcache.get(dirName)!=null) {
            throw new JafsException("directory "+dirName+" already in cache");
        }
        gcache.add(dirName, entry);
    }

    JafsDirEntry get(String dirName) {
        return gcache.get(dirName);
    }

    void remove(String dirName) {
        gcache.remove(dirName);
    }
}
