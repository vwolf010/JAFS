package nl.v4you.jafs;

import nl.v4you.jafs.internal.JafsDir;
import nl.v4you.jafs.internal.JafsDirEntry;
import nl.v4you.jafs.internal.JafsInode;

import java.io.IOException;
import java.io.OutputStream;

public class JafsOutputStream extends OutputStream {
	private final Jafs vfs;
	private final String path;
	private final long oldSize;
	private JafsInode inode;

	JafsOutputStream(Jafs vfs, JafsFile f, boolean append) throws JafsException, IOException {
		this.vfs = vfs;
		if (!f.exists() && !f.createNewFile()) {
			throw new JafsException("Could not create new file: " + f.getCanonicalPath());
		}
		this.path = f.getCanonicalPath();
		JafsDirEntry entry = f.getEntry(f.getCanonicalPath());
		if (entry != null && entry.getBpos() != 0) {
			inode = new JafsInode(vfs);
			inode.openInode(entry.getBpos());
			oldSize = inode.getSize();
			if (append) {
				inode.seekEnd(0);
			}
			else {
				inode.resetSize();
				vfs.flushBlockCache();
			}
		}
		else {
			oldSize = 0;
		}
	}
	
	@Override
	public void flush() throws IOException {
		super.flush();
	}

	private void createInode() throws IOException {
		try {
			JafsFile f = new JafsFile(vfs, path);
			JafsInode inodeDirectory = vfs.getInodePool().claim();
			JafsDir dir = vfs.getDirPool().claim();
			try {
				inodeDirectory.openInode(f.getEntry(f.getParent()).getBpos());
				dir.setInode(inodeDirectory);
				JafsDirEntry entry = f.getEntry(path);
				if (entry == null) {
					throw new JafsException("No entry found for [" + path + "]");
				}
				dir.mkinode(entry, JafsInode.INODE_FILE);
				inode = new JafsInode(vfs);
				inode.openInode(entry.getBpos());
			}
			finally {
				vfs.getInodePool().release(inodeDirectory);
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
			if (inode == null) {
				createInode();
			}
			inode.writeByte(b);
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
			if (inode == null) {
				createInode();
			}
			inode.writeBytes(buf, start, len);
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
			JafsInode inodeDirectory = vfs.getInodePool().claim();
			JafsDir dir = vfs.getDirPool().claim();
			try {
				inodeDirectory.openInode(f.getEntry(f.getParent()).getBpos());
				dir.setInode(inodeDirectory);
				JafsDirEntry entry = f.getEntry(path);
				if (entry == null) {
					throw new JafsException("No entry found for [" + path + "]");
				}
				dir.entryClearInodePtr(entry);
			}
			finally {
				vfs.getInodePool().release(inodeDirectory);
				vfs.getDirPool().release(dir);
			}
		} catch (JafsException e) {
			e.printStackTrace();
			throw new IOException("VFSExcepion wrapper: " + e.getMessage());
		}
	}

	@Override
	public void close() throws IOException {
		if (inode != null) {
			try {
				if (inode.getSize() == 0) {
					deleteDirEntry();
					inode.freeBlocksAndDeleteInode();
				}
				else {
					inode.freeBlocks(oldSize);
				}
				vfs.flushBlockCache();
			} catch (JafsException e) {
				throw new RuntimeException(e);
			}
		}
		super.close();
	}
}
