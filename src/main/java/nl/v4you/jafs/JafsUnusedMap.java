package nl.v4you.jafs;

import java.io.IOException;
import java.util.Set;

public interface JafsUnusedMap {
    long getUnusedMapBpos(long bpos);
    void createNewUnusedMap(Set<Long> blockList, long bpos) throws JafsException, IOException;
    long getUnusedINodeBpos(Set<Long> blockList) throws JafsException, IOException;
    void setAvailableForNeither(Set<Long> blockList, long bpos) throws JafsException, IOException;
    void setAvailableForInodeOnly(Set<Long> blockList, long bpos) throws JafsException, IOException;
    void setAvailableForBoth(Set<Long> blockList, long bpos) throws JafsException, IOException;
    void setStartAtInode(long bpos);
    void setStartAtData(long bpos);
    long getUnusedDataBpos(Set<Long> blockList) throws JafsException, IOException;
}
