package nl.v4you.jafs;

import java.io.IOException;
import java.io.OutputStream;

public class JafsOutputStream extends OutputStream {
	private final Jafs vfs;
	private final String path;
	private final long oldSize;
	private ZFile zfile;

	JafsOutputStream(Jafs vfs, JafsFile f, boolean append) throws JafsException, IOException {
		this.vfs = vfs;
		if (!f.exists() && !f.createNewFile()) {
			throw new JafsException("Could not create new file: " + f.getCanonicalPath());
		}
		this.path = f.getCanonicalPath();
		ZDirEntry entry = f.getEntry(f.getCanonicalPath());
		if (entry != null && entry.getVpos() != 0) {
			zfile = new ZFile(vfs);
			zfile.openInode(entry.getVpos());
			oldSize = zfile.size;
			if (append) {
				zfile.seekEnd(0);
			} else {
				zfile.resetSize();
				vfs.flushBlockCache();
			}
		} else {
			oldSize = 0;
		}
	}
	
	@Override
	public void flush() throws IOException {
		super.flush();
	}

	private void createFile() throws IOException {
		try {
			JafsFile f = new JafsFile(vfs, path);
			ZFile inodeDirectory = vfs.getZFilePool().claim();
			ZDir dir = vfs.getDirPool().claim();
			try {
				inodeDirectory.openInode(f.getEntry(f.getParent()).getVpos());
				dir.setInode(inodeDirectory);
				ZDirEntry entry = f.getEntry(path);
				if (entry == null) {
					throw new JafsException("No entry found for [" + path + "]");
				}
				dir.mkinode(entry, ZFile.INODE_FILE);
				zfile = new ZFile(vfs);
				zfile.openInode(entry.getVpos());
			}
			finally {
				vfs.getZFilePool().release(inodeDirectory);
				vfs.getDirPool().release(dir);
			}
		} catch (JafsException e) {
			e.printStackTrace();
			throw new IOException("VFSExcepion wrapper: "+e.getMessage());
		}
	}

	@Override
	public void write(int b) throws IOException {
		try {
			if (zfile == null) {
				createFile();
			}
			zfile.writeByte(b);
			vfs.flushBlockCache();
		} catch (JafsException e) {
			e.printStackTrace();
			throw new IOException("VFSExcepion wrapper: "+e.getMessage());
		}
	}

	@Override
	public void write(byte[] buf, int start, int len) throws IOException {
		if (buf == null) {
			throw new NullPointerException("buf cannot be null");
		}
		if (start < 0) {
			throw new IllegalStateException("start must be >= 0");
		}
		if (len == 0) {
			return;
		}
		if (len < 0) {
			throw new IllegalStateException("length must be >= 0");
		}
		try {
			if (zfile == null) {
				createFile();
			}
			zfile.writeBytes(buf, start, len);
			vfs.flushBlockCache();
		} catch (JafsException e) {
			e.printStackTrace();
			throw new IOException("VFSExcepion wrapper: "+e.getMessage());
		}
	}

	@Override
	public void write(byte[] buf) throws IOException {
		write(buf, 0, buf.length);
	}

	private void deleteDirEntry() throws IOException{
		try {
			JafsFile f = new JafsFile(vfs, path);
			ZFile inodeDirectory = vfs.getZFilePool().claim();
			ZDir dir = vfs.getDirPool().claim();
			try {
				inodeDirectory.openInode(f.getEntry(f.getParent()).getVpos());
				dir.setInode(inodeDirectory);
				ZDirEntry entry = f.getEntry(path);
				if (entry == null) {
					throw new JafsException("No entry found for [" + path + "]");
				}
				dir.entryClearInodePtr(entry);
			}
			finally {
				vfs.getZFilePool().release(inodeDirectory);
				vfs.getDirPool().release(dir);
			}
		} catch (JafsException e) {
			e.printStackTrace();
			throw new IOException("VFSExcepion wrapper: " + e.getMessage());
		}
	}

	@Override
	public void close() throws IOException {
		if (zfile != null) {
			try {
				if (zfile.size == 0) {
					deleteDirEntry();
					zfile.freeBlocksAndDeleteInode();
				} else {
					zfile.freeBlocks(oldSize);
				}
				vfs.flushBlockCache();
			} catch (JafsException e) {
				throw new RuntimeException(e);
			}
		}
		super.close();
	}
}
