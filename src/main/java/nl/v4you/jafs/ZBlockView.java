package nl.v4you.jafs;

import java.io.IOException;

class ZBlockView {
    private final long blockId;
    private final int viewSize;
    private final ZBlockCache blockCache;
    private final int byteOffset;

    private ZBlock diskBlock;
    private int byteIdx;

    ZBlockView(Jafs vfs, long vpos) {
        blockCache = vfs.getBlockCache();
        viewSize = vfs.getSuper().getBlockSize();
        int viewsPerBlock = 4096 / viewSize;
        blockId = (vpos * viewSize) / 4096;
        byteOffset = (int)((vpos % viewsPerBlock) * viewSize);
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
