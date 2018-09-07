package nl.v4you.jafs;

import java.io.IOException;
import java.io.OutputStream;

public class JafsOutputStream extends OutputStream {
	Jafs vfs;
	JafsInode inode;
	String path;
	
	JafsOutputStream(Jafs vfs, JafsFile f, boolean append) throws JafsException, IOException {
		this.vfs = vfs;
		if (!f.exists() && !f.createNewFile()) {
			throw new JafsException("Could not create "+f.getCanonicalPath());
		}
		this.path = f.getCanonicalPath();
		JafsDirEntry entry = f.getEntry(f.getCanonicalPath());
		if (entry!=null) {
			if (entry.bpos>0) {
				inode = new JafsInode(vfs, entry);
				if (append) {
					inode.seekEnd(0);
				}
//				else {
//					inode.size = 0;
//				}
			}
		}

//		this.vfs = vfs;
//		if (!append && f.exists() && !f.delete()) {
//			throw new JafsException("Could not delete " + f.getCanonicalPath());
//		}
//		if (!f.exists() && !f.createNewFile()) {
//			throw new JafsException("Could not create " + f.getCanonicalPath());
//		}
//		this.path = f.getCanonicalPath();
//		JafsDirEntry entry = f.getEntry(f.getCanonicalPath());
//		if (entry!=null) {
//			if (entry.bpos>0) {
//				inode = new JafsInode(vfs, entry);
//				if (append) {
//					inode.seekSet(0, JafsInode.SEEK_END);
//				}
//			}
//		}
	}
	
	@Override
	public void flush() throws IOException {
		super.flush();
	}
	
	@Override
	public void close() throws IOException {
		super.close();
	}

	private void createInodeIfNeeded() throws IOException {
		try {
			if (inode==null) {
				JafsFile f = new JafsFile(vfs, path);
				JafsDir dir = new JafsDir(vfs, f.getEntry(f.getParent()));
				dir.mkinode(f.getName().getBytes("UTF-8"), JafsInode.INODE_FILE);
				inode = new JafsInode(vfs, f.getEntry(path));
			}
		} catch (JafsException e) {
			e.printStackTrace();
			throw new IOException("VFSExcepion wrapper: "+e.getMessage());
		}
	}

	@Override
	public void write(int arg0) throws IOException {
		try {
			createInodeIfNeeded();
			inode.writeByte(arg0);
		} catch (JafsException e) {
			e.printStackTrace();
			throw new IOException("VFSExcepion wrapper: "+e.getMessage());
		}
	}

	@Override
	public void write(byte buf[], int start, int len) throws IOException {
		try {
			createInodeIfNeeded();
			inode.writeBytes(buf, start, len);
		} catch (JafsException e) {
			e.printStackTrace();
			throw new IOException("VFSExcepion wrapper: "+e.getMessage());
		}
	}

	@Override
	public void write(byte buf[]) throws IOException {
		write(buf, 0, buf.length);
	}
}