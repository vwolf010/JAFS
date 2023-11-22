package nl.v4you.jafs;

import nl.v4you.jafs.internal.JafsDir;
import nl.v4you.jafs.internal.JafsDirEntry;
import nl.v4you.jafs.internal.JafsInode;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.TreeSet;

public class JafsOutputStream extends OutputStream {
	Jafs vfs;
	JafsInode inode;
	String path;
	
	JafsOutputStream(Jafs vfs, JafsFile f, boolean append) throws JafsException, IOException {
		this.vfs = vfs;
		if (!f.exists() && !f.createNewFile()) {
			throw new JafsException("Could not appendNewBlockToArchive "+f.getCanonicalPath());
		}
		this.path = f.getCanonicalPath();
		JafsDirEntry entry = f.getEntry(f.getCanonicalPath());
		if (entry != null && entry.getBpos() != 0) {
			inode = new JafsInode(vfs);
			inode.openInode(entry.getBpos());
			if (append) {
				inode.seekEnd(0);
			}
		}
	}
	
	@Override
	public void flush() throws IOException {
		super.flush();
	}

	private void createInode(Set<Long> blockList) throws IOException {
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
				dir.mkinode(blockList, entry, JafsInode.INODE_FILE);
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
	public void write(int arg0) throws IOException {
		try {
			Set<Long> blockList = new TreeSet<>();
			if (inode == null) {
				createInode(blockList);
			}
			inode.writeByte(blockList, arg0);
			vfs.getBlockCache().flushBlocks(blockList);
		} catch (JafsException e) {
			e.printStackTrace();
			throw new IOException("VFSExcepion wrapper: "+e.getMessage());
		}
	}

	@Override
	public void write(byte[] buf, int start, int len) throws IOException {
		try {
			Set<Long> blockList = new TreeSet<>();
			if (inode == null) {
				createInode(blockList);
			}
			inode.writeBytes(blockList, buf, start, len);
			vfs.getBlockCache().flushBlocks(blockList);
		} catch (JafsException e) {
			e.printStackTrace();
			throw new IOException("VFSExcepion wrapper: "+e.getMessage());
		}
	}

	@Override
	public void write(byte[] buf) throws IOException {
		write(buf, 0, buf.length);
	}

	@Override
	public void close() throws IOException {
		try {
			Set<Long> blockList = new TreeSet<>();
			inode.free(blockList);
			if (inode.getSize() == 0) {
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
						dir.deleteEntry(blockList, f.getCanonicalPath(), entry);
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
			vfs.getBlockCache().flushBlocks(blockList);
		} catch (JafsException e) {
			throw new RuntimeException(e);
		}
		super.close();
	}
}
