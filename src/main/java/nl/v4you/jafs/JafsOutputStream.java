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
			throw new JafsException("Could not appendNewBlockToArchive "+f.getCanonicalPath());
		}
		this.path = f.getCanonicalPath();
		JafsDirEntry entry = f.getEntry(f.getCanonicalPath());
		if (entry!=null) {
			if (entry.bpos>0) {
				inode = new JafsInode(vfs, entry);
				if (append) {
					inode.seekEnd(0);
				}
			}
		}
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
				JafsDirEntry entry = f.getEntry(path);
				if (entry==null) {
				    throw new JafsException("No entry found for ["+path+"]");
                }
				dir.mkinode(
						entry,
						JafsInode.INODE_FILE);
				inode = new JafsInode(vfs, entry);
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
