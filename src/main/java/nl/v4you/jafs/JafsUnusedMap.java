package nl.v4you.jafs;

import java.io.IOException;

public interface JafsUnusedMap {
    long getUnusedMapBpos(long bpos);
    void createNewUnusedMap(long bpos) throws JafsException, IOException;
    long getUnusedINodeBpos() throws JafsException, IOException;
    void setAvailableForNeither(long bpos) throws JafsException, IOException;
    void setAvailableForInodeOnly(long bpos) throws JafsException, IOException;
    void setAvailableForBoth(long bpos) throws JafsException, IOException;
    void setStartAtInode(long bpos);
    void setStartAtData(long bpos);
    long getUnusedDataBpos() throws JafsException, IOException;
}
