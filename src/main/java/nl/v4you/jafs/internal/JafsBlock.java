package nl.v4you.jafs.internal;

import nl.v4you.jafs.Jafs;
import nl.v4you.jafs.JafsException;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class JafsBlock {
	private final int blockSize = 4096;
	private final byte[] buf;
	public long bpos;
	private final RandomAccessFile raf;
	public int byteIdx;
	boolean needsFlush = false;
	final Jafs vfs;
	final JafsBlockCache blockCache;

	JafsBlock(Jafs vfs, long bpos) {
		this.vfs = vfs;
		this.blockCache = vfs.getBlockCache();
		raf = vfs.getRaf();
		buf = new byte[blockSize];
		this.bpos = bpos;
		byteIdx = 0;
	}

	void setBpos(long bpos) {
	    this.bpos = bpos;
        byteIdx = 0;
    }

	void initZeros(int len) {
		if (len == 0) {
			return;
		}
		Arrays.fill(buf, byteIdx, byteIdx + len, (byte)0);
		byteIdx += len;
        markForFlush();
	}

	void initOnes(int len) {
		if (len == 0) {
			return;
		}
		Arrays.fill(buf, byteIdx, byteIdx + len, (byte)0xff);
		byteIdx += len;
		markForFlush();
	}
	
	void seekSet(int b) {
		byteIdx = b;
	}
		
	void readFromDisk() throws IOException, JafsException {
		if (needsFlush) {
			throw new JafsException("cannot read from disk when needsFlush == true");
		}
		long start = bpos * blockSize;
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
		long start = bpos * blockSize;
		raf.seek(start);
		raf.write(buf);
		needsFlush = false;
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

	void writeBytes(byte[] b, int off, int len) {
		if (len == 0) {
			return;
		}
		System.arraycopy(b, off, buf, byteIdx, len);
		byteIdx += len;
        markForFlush();
	}

	long readInt() {
		long i = (buf[byteIdx++] & 0xffL) << 24;
		i |= (buf[byteIdx++] & 0xffL) << 16;
		i |= (buf[byteIdx++] & 0xffL) << 8;
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
