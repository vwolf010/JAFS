package nl.v4you.jafs.internal;

import nl.v4you.jafs.Jafs;
import nl.v4you.jafs.JafsException;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class JafsBlock {
	private static final int SUPERBLOCK_SIZE = 1;
	private final int blockSize;
	private final byte[] buf;
	public long bpos;
	private final RandomAccessFile raf;
	public int byteIdx;
	boolean needsFlush = false;
	final Jafs vfs;
	final JafsBlockCache blockCache;

	int bytesLeft() {
		return blockSize-byteIdx;
	}

	JafsBlock(Jafs vfs, long bpos) {
		this(vfs, bpos, -1);
	}
	
	JafsBlock(Jafs vfs, long bpos, int blockSize) {
		this.vfs = vfs;
		this.blockCache = vfs.getBlockCache();
		raf = vfs.getRaf();
		if (blockSize < 0) {
			blockSize = vfs.getSuper().getBlockSize();
		}
		this.blockSize = blockSize;
		buf = new byte[blockSize];
		this.bpos = bpos;
		byteIdx = 0;
	}

	void setBpos(long bpos) {
	    this.bpos = bpos;
        byteIdx = 0;
    }

	void initZeros() {
		Arrays.fill(buf, (byte)0);
        markForFlush();
	}

	void initOnes() {
		Arrays.fill(buf, (byte)0xff);
		markForFlush();
	}
	
	void seekSet(int b) {
		byteIdx = b;
	}
		
	void readFromDisk() throws IOException, JafsException {
		if (needsFlush) {
			throw new JafsException("cannot read from disk when needsFlush == true");
		}
		long start = (SUPERBLOCK_SIZE + bpos) * blockSize;
		raf.seek(start);
		raf.read(buf);
		byteIdx = 0;
	}

	void markForFlush() {
		if (!needsFlush) {
			blockCache.addToFlushList(bpos);
			needsFlush = true;
		}
	}
	void writeToDisk() throws IOException {
		long start = (SUPERBLOCK_SIZE + bpos) * blockSize;
		raf.seek(start);
		raf.write(buf);
		needsFlush = false;
	}

//	void dumpBlock(File f) {
//		try {
//			FileOutputStream fos = new FileOutputStream(f);
//			fos.write(buf);
//			fos.close();
//		}
//		catch (Exception e) {
//			System.err.println("Failed to dump block "+f.getAbsolutePath());
//		}
//	}
//
//	void dumpBlock() {
//		File f = new File("c:/data/temp/inode_block_"+bpos+".dmp");
//		dumpBlock(f);
//	}

	int peekSkipMapByte() {
		return buf[0] & 0xff;
	}

	void pokeSkipMapByte(int b) {
		buf[0] = (byte)b;
        markForFlush();
	}

	int readByte() {
		return buf[byteIdx++] & 0xff;
	}

	void writeByte(int b) {
		buf[byteIdx++] = (byte)b;
        markForFlush();
	}

	int peekByte() {
		return buf[byteIdx];
	}

	void pokeByte(int b) {
		buf[byteIdx] = (byte)b;
        markForFlush();
	}

	int peekByte(int idx) {
		return buf[idx] & 0xff;
	}

	void pokeByte(int idx, int b) {
		buf[idx] = (byte)b;
        markForFlush();
	}

	void readBytes(byte[] b, int off, int len) {
		if (len == 0) {
			return;
		}
		System.arraycopy(buf, byteIdx, b, off, len);
		byteIdx += len;
	}

	void readBytes(byte[] b, int len) {
		if (len == 0) {
			return;
		}
		System.arraycopy(buf, byteIdx, b, 0, len);
		byteIdx += len;
	}

	void writeBytes(byte[] b) {
		writeBytes(b, b.length);
	}

	void writeBytes(byte[] b, int off, int len) {
		if (len == 0) {
			return;
		}
		System.arraycopy(b, off, buf, byteIdx, len);
		byteIdx += len;
        markForFlush();
	}

	void writeBytes(byte[] b, int len) {
		if (len == 0) {
			return;
		}
		System.arraycopy(b, 0, buf, byteIdx, len);
		byteIdx += len;
		markForFlush();
	}
	long readInt() {
		long i = 0;
		i |= (buf[byteIdx++] & 0xffL)<<24;
		i |= (buf[byteIdx++] & 0xffL)<<16;
		i |= (buf[byteIdx++] & 0xffL)<< 8;
		i |= (buf[byteIdx++] & 0xffL);
		return i;
	}

	void writeInt(long l) {
		buf[byteIdx++] = (byte)((l >>> 24) & 0xffL);
		buf[byteIdx++] = (byte)((l >>> 16) & 0xffL);
		buf[byteIdx++] = (byte)((l >>>  8) & 0xffL);
		buf[byteIdx++] = (byte)(l & 0xffL);
		markForFlush();
	}
}
