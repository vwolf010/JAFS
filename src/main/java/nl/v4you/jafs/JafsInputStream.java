package nl.v4you.jafs;

import java.io.IOException;
import java.io.InputStream;

public class JafsInputStream extends InputStream {
	Jafs vfs;
	String path;
	JafsInode inode;
	
	JafsInputStream(Jafs vfs, JafsFile f) throws JafsException, IOException {
		this.vfs = vfs;
		path = f.getPath();
		JafsDirEntry entry = f.getEntry(path);
		if (entry.bpos>0) {
			inode = new JafsInode(vfs);
			inode.openInode(entry);
		}
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int bread = 0;
		try {
			if (inode==null) {
				JafsFile f = new JafsFile(vfs, path);
				JafsDirEntry entry = f.getEntry(f.getPath());
				if (entry.bpos>0) {
					inode = new JafsInode(vfs);
					inode.openInode(entry);
				}
			}
			if (inode!=null) {
				bread = inode.readBytes(b, off, len);
			}
		} catch (JafsException e) {
			throw new IOException("VFSException wrapper: "+e.getMessage());
		}
		return bread;
	}
	
	@Override
	public int read() throws IOException {
		int b = -1;
		try {
			if (inode==null) {
				JafsFile f = new JafsFile(vfs, path);
				JafsDirEntry entry = f.getEntry(f.getPath());
				if (entry.bpos>0) {
					inode = new JafsInode(vfs);
					inode.openInode(entry);
				}			
			}
			if (inode!=null) {
				return inode.readByte();
			}
		} catch (JafsException e) {
			throw new IOException("VFSException wrapper: "+e.getMessage());
		}
		return b;
	}
}
