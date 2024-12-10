package nl.v4you.jafs.internal;

import nl.v4you.jafs.Jafs;
import nl.v4you.jafs.JafsException;

import java.io.IOException;

public class JafsBlockView {
    private static final int SUPERBLOCK_SIZE = 1;

    private final long blockId;
    private final int viewSize;
    private final JafsBlockCache blockCache;
    private final int byteOffset;

    private JafsBlock diskBlock;
    private int byteIdx;

    JafsBlockView(Jafs vfs, long vpos) {
        blockCache = vfs.getBlockCache();
        viewSize = vfs.getSuper().getBlockSize();
        int viewsPerBlock = 4096 / viewSize;
        blockId = ((SUPERBLOCK_SIZE + vpos) * viewSize) / 4096;
        byteOffset = (int)(((SUPERBLOCK_SIZE + vpos) % viewsPerBlock) * viewSize);
        byteIdx = 0;
    }

    void loadDiskBlockIfNeeded() throws JafsException, IOException {
        if (diskBlock == null) {
            diskBlock = blockCache.get(blockId);
        }
    }

    void seekSet(int b) {
        byteIdx = b;
    }

    void writeBytes(byte[] b, int len) throws JafsException, IOException {
        writeBytes(b, 0, len);
    }

    void writeBytes(byte[] b, int off, int len) throws JafsException, IOException {
        loadDiskBlockIfNeeded();
        diskBlock.seekSet(byteOffset + byteIdx);
        diskBlock.writeBytes(b, off, len);
        byteIdx += len;
    }

    void readBytes(byte[] b, int off, int len) throws JafsException, IOException {
        loadDiskBlockIfNeeded();
        diskBlock.seekSet(byteOffset + byteIdx);
        diskBlock.readBytes(b, off, len);
        byteIdx += len;
    }

    void readBytes(byte[] b, int len) throws JafsException, IOException {
        readBytes(b, 0, len);
    }

    void writeByte(int b) throws JafsException, IOException {
        loadDiskBlockIfNeeded();
        diskBlock.seekSet(byteOffset + byteIdx);
        diskBlock.writeByte(b);
        byteIdx++;
    }

    int bytesLeft() {
        return viewSize - byteIdx;
    }

    int readByte() throws JafsException, IOException {
        loadDiskBlockIfNeeded();
        diskBlock.seekSet(byteOffset + byteIdx);
        byteIdx++;
        return diskBlock.readByte();
    }

    void initZeros() throws JafsException, IOException {
        loadDiskBlockIfNeeded();
        diskBlock.seekSet(byteOffset);
        diskBlock.initZeros(viewSize);
    }

    long readInt() throws JafsException, IOException {
        loadDiskBlockIfNeeded();
        diskBlock.seekSet(byteOffset + byteIdx);
        byteIdx += 4;
        return diskBlock.readInt();
    }

    void writeInt(long l) throws JafsException, IOException {
        loadDiskBlockIfNeeded();
        diskBlock.seekSet(byteOffset + byteIdx);
        diskBlock.writeInt(l);
        byteIdx += 4;
    }
}
