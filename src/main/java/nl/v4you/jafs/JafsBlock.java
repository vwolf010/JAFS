package nl.v4you.jafs;

import java.io.*;
import java.util.Arrays;

class JafsBlock {

	private int blockSize = -1;
	private byte[] buf;
	public long bpos = -1;
	private RandomAccessFile raf;
	public int byteIdx = 0;
	boolean isSaved=false;

	int bytesLeft() {
		return blockSize-byteIdx;
	}

	private void init(Jafs vfs) {
		raf = vfs.getRaf();
		if (blockSize<0) {
			blockSize = vfs.getSuper().getBlockSize();
		}
		buf = new byte[blockSize];
	}

	JafsBlock(Jafs vfs, long bpos) {
		init(vfs);
		this.bpos = bpos;
		byteIdx = 0;
	}
	
	JafsBlock(Jafs vfs, long bpos, int blockSize) {
		this.blockSize = blockSize;
		init(vfs);
		this.bpos = bpos;
		byteIdx = 0;
	}

	void initZeros() {
		Arrays.fill(buf, (byte)0);
	}
	
	void seekSet(int b) {
		byteIdx = b;
	}
		
	void readFromDisk() throws IOException {
		long start = (1+bpos) * blockSize;
		raf.seek(start);
		raf.read(buf);
		byteIdx = 0;
	}
	
	void writeToDisk() throws IOException, JafsException {
		long start = (1+bpos) * blockSize;
		long end = start + blockSize;
		if (end>raf.length()) {
			throw new JafsException("Trying to write beyond filesize");
		}
		raf.seek(start);
		raf.write(buf);
		isSaved=true;
	}

	void dumpBlock(File f) {
		try {
			FileOutputStream fos = new FileOutputStream(f);
			fos.write(buf);
			fos.close();
		}
		catch (Exception e) {
			System.err.println("Failed to dump block "+f.getAbsolutePath());
		}
	}

	void dumpBlock() {
		File f = new File("c:/data/temp/inode_block_"+bpos+".dmp");
		dumpBlock(f);
	}

	int readByte() throws JafsException {
		if (byteIdx+1>blockSize) {
			throw new JafsException("Trying to read beyond buffer");
		}
		return buf[byteIdx++] & 0xff;
	}

	void writeByte(int b) throws JafsException {
		if (byteIdx+1>blockSize) {
			throw new JafsException("Trying to write beyond buffer");
		}
		buf[byteIdx++] = (byte)(b & 0xff);
	}

	void readBytes(byte b[], int off, int len) {
		if (b == null) {
			throw new NullPointerException();
		} else if (off < 0 || len < 0 || len > b.length - off) {
			throw new IndexOutOfBoundsException();
		} else if (byteIdx+len>blockSize) {
			throw new IllegalStateException("Trying to read beyond block, byteIdx="+byteIdx+", len="+len+", blockSize="+blockSize);
		} else if (len == 0) {
			return;
		}
		System.arraycopy(buf, byteIdx, b, off, len);
		byteIdx += len;
	}

	void writeBytes(byte b[], int off, int len) {
		if (b == null) {
			throw new NullPointerException();
		} else if (off < 0 || len < 0 || len > b.length - off) {
			throw new IndexOutOfBoundsException();
		} else if (byteIdx+len>blockSize) {
			throw new IllegalStateException("Trying to write beyond block");
		} else if (len == 0) {
			return;
		}
		System.arraycopy(b, off, buf, byteIdx, len);
		byteIdx+=len;
	}
		
	long readInt() throws JafsException {
		if (byteIdx+4>blockSize) {
			throw new JafsException("Trying to read beyond buffer");
		}
		long i = 0;
		i |= (buf[byteIdx++] & 0xffL)<<24;
		i |= (buf[byteIdx++] & 0xffL)<<16;
		i |= (buf[byteIdx++] & 0xffL)<< 8;
		i |= (buf[byteIdx++] & 0xffL);
		return i;
	}

	void writeInt(long l) throws JafsException {
		if (byteIdx+4>blockSize) {
			throw new JafsException("Trying to write beyond buffer");
		}
		buf[byteIdx++] = (byte)((l >> 24) & 0xffL);
		buf[byteIdx++] = (byte)((l >> 16) & 0xffL);
		buf[byteIdx++] = (byte)((l >>  8) & 0xffL);
		buf[byteIdx++] = (byte)(l & 0xffL);
	}

	long readLong() throws JafsException {
		if (byteIdx+8>blockSize) {
			throw new JafsException("Trying to read beyond buffer");
		}
		long l = 0;
		l |= (long)(buf[byteIdx++] & 0xffL)<<56;
		l |= (long)(buf[byteIdx++] & 0xffL)<<48;
		l |= (long)(buf[byteIdx++] & 0xffL)<<40;
		l |= (long)(buf[byteIdx++] & 0xffL)<<32;
		l |= (long)(buf[byteIdx++] & 0xffL)<<24;
		l |= (long)(buf[byteIdx++] & 0xffL)<<16;
		l |= (long)(buf[byteIdx++] & 0xffL)<< 8;
		l |= (long)(buf[byteIdx++] & 0xffL);

		return l;
	}
	
	void writeLong(long l) throws JafsException {
		if (byteIdx+8>blockSize) {
			throw new JafsException("Trying to write beyond buffer");
		}
		buf[byteIdx++] = (byte)((l >> 56) & 0xffL);
		buf[byteIdx++] = (byte)((l >> 48) & 0xffL);
		buf[byteIdx++] = (byte)((l >> 40) & 0xffL);
		buf[byteIdx++] = (byte)((l >> 32) & 0xffL);
		buf[byteIdx++] = (byte)((l >> 24) & 0xffL);
		buf[byteIdx++] = (byte)((l >> 16) & 0xffL);
		buf[byteIdx++] = (byte)((l >>  8) & 0xffL);
		buf[byteIdx++] = (byte)(l & 0xff);
	}

	byte[] readBytes(int i) throws JafsException {
		byte[] b = new byte[i];
		readBytes(b, 0, i);
		return b;
	}
	
	void writeBytes(byte[] b) throws JafsException {
		writeBytes(b, 0, b.length);
	}

	short readShort() throws JafsException {
		short s = 0;
		s |= (short)(readByte() & 0xff)<<8;
		s |= (short)(readByte() & 0xff);
		return s;
	}

	void writeShort(short s) throws JafsException {
		writeByte(((s >>  8) & 0xff));
		writeByte((s & 0xff));
	}

	long readLongVar() throws JafsException {
		int shift = 0;
		long l = 0;
		int b = readByte();
		while ((b & 0x80)>0) {
			l |= (b & 0x7fL) << shift;
			shift += 7;
			b = readByte();
		}
		l |= b << shift;
		return l;
	}

	void writeLongVar(long l) throws JafsException {
		while (l != (l & 0x7f)) {
			writeByte((int)(0x80 | (l & 0x7f)));
			l >>= 7;
		}
		writeByte((int)l);
	}
}
