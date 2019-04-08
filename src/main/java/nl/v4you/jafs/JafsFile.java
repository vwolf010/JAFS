package nl.v4you.jafs;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class JafsFile {
    public static final String separator = "/";

    private Jafs vfs;
	private String path;
	private String canonicalPath;
    private JafsDirEntryCache dc;

    private static final Pattern SLASH = Pattern.compile("/");
    private static final Pattern MULTIPLE_SLASH = Pattern.compile("/+/");
    private static final Pattern FILENAME_IN_PATH = Pattern.compile("/[^/]*$");
    private static final Pattern SLASH_DOT_SLASH = Pattern.compile("/[.]/");
	private static final Pattern FILE_CANCELS_ITSELF = Pattern.compile("[^/]+/[.][.]/");

	JafsFile(Jafs vfs, String path) throws JafsException {
		if (path==null) {
			throw new NullPointerException("path cannot be null");
		}
		path = path.trim();
		if (!path.startsWith("/")) {
		    throw new JafsException("Path must always start with /");
		}
		this.vfs = vfs;
		this.dc = vfs.getDirCache();
		this.path = normalizePath(path);
		this.canonicalPath = getCanonicalPath(this.path);
    }

    JafsFile(Jafs vfs, JafsFile parent, String child) throws JafsException {
        this(vfs, parent.getCanonicalPath()+JafsFile.separator +child);
    }

    JafsFile(Jafs vfs, String parent, String child) throws JafsException {
        this(vfs, parent+JafsFile.separator +child);
    }

	/*
	 * public 
	 */
	public String getName() {
		return getName(path);
	}

	public String getPath() {
		return path;
	}

	public String getParent() {
		return getParent(path);
	}

	public String getAbsolutePath() {
		return getAbsolutePath(path);
	}

	public String getCanonicalPath() {
		return canonicalPath;
	}

	public boolean exists() throws JafsException, IOException {
		return exists(canonicalPath);
	}

	public boolean isFile() throws JafsException, IOException {
		JafsDirEntry entry = getEntry(canonicalPath);
		return (entry != null) && (entry.isFile());
	}
	
	public boolean isDirectory() throws JafsException, IOException {
		JafsDirEntry entry = getEntry(canonicalPath);
		return (entry != null) && (entry.isDirectory());
	}

	public long length() throws IOException, JafsException {
		JafsDirEntry entry = getEntry(canonicalPath);
		if (entry==null || entry.bpos==0) {
			return 0;
		}
        JafsInode inode = vfs.getInodePool().get();
		try {
            inode.openInode(entry);
            long inodeSize = inode.size;
            return inodeSize;
        }
        finally {
            vfs.getInodePool().free(inode);
        }
	}

	public boolean createNewFile() throws JafsException, IOException {
		String parentPath = getParent(canonicalPath);
		JafsDirEntry parent = getEntry(parentPath);
		Set<Long> blockList = new TreeSet<>();
		if (parent!=null) {
			if (parent.bpos==0) {
				// Parent exists but has no inode yet, let's create it
                JafsInode inode = vfs.getInodePool().get();
                JafsDir dir = vfs.getDirPool().get();
                try {
                    inode.openInode(parent.parentBpos, parent.parentIpos);
                    dir.setInode(inode);
                    dir.mkinode(blockList, parent, JafsInode.INODE_DIR);
                }
                finally {
                    vfs.getInodePool().free(inode);
                    vfs.getDirPool().free(dir);
                }
			}
			JafsInode inode = vfs.getInodePool().get();
            JafsDir dir = vfs.getDirPool().get();
			try {
                inode.openInode(parent);
                dir.setInode(inode);
                dir.createNewEntry(blockList, canonicalPath, getName().getBytes(Util.UTF8), JafsInode.INODE_FILE, 0, 0);
                return true;
            }
            catch (Throwable t) {
			    return false;
            }
            finally {
			    vfs.flushChanges(blockList);
                vfs.getInodePool().free(inode);
                vfs.getDirPool().free(dir);
            }
		}
		return false;
	}

	public boolean mkdir() throws JafsException, IOException {
        Set<Long> blockList = new TreeSet<>();
        boolean b = mkdir(blockList, canonicalPath);
        vfs.flushChanges(blockList);
		return b;
	}

	public boolean mkdirs() throws JafsException, IOException {
        Set<Long> blockList = new TreeSet<>();
        mkParentDirs(blockList, getParent(canonicalPath));
		boolean b = mkdir(blockList, canonicalPath);
		vfs.flushChanges(blockList);
		return b;
	}
		
	public String[] list() throws JafsException, IOException {
		JafsDirEntry entry = getEntry(canonicalPath);
		if (entry!=null) {
			if (entry.bpos==0) {
				return new String[0];
			}
			else {
			    JafsInode inode = vfs.getInodePool().get();
                JafsDir dir = vfs.getDirPool().get();
			    try {
                    inode.openInode(entry);
                    dir.setInode(inode);
                    String lst[] = dir.list();
                    return lst;
                }
                finally {
                    vfs.getInodePool().free(inode);
                    vfs.getDirPool().free(dir);
                }
			}
		}
		return new String[0];
	}
	
	public boolean delete() throws JafsException, IOException {
		JafsDirEntry entry = getEntry(canonicalPath);
		if (entry!=null) {
			if (entry.bpos>0) {
				if (entry.isDirectory()) {
				    JafsInode inode = vfs.getInodePool().get();
                    JafsDir dir = vfs.getDirPool().get();
				    try {
                        inode.openInode(entry);
                        dir.setInode(inode);
                        if (dir.hasActiveEntries()) {
                            throw new JafsException("directory " + canonicalPath + " not empty");
                        }
                    }
                    finally {
                        vfs.getInodePool().free(inode);
                        vfs.getDirPool().free(dir);
                    }
				}
			}
			JafsInode inode = vfs.getInodePool().get();
            JafsDir parentDir = vfs.getDirPool().get();
            Set<Long> blockList = new TreeSet<>();
			try {
			    // first remove the entry from the directory
                inode.openInode(entry.parentBpos, entry.parentIpos);
                parentDir.setInode(inode);
				{
					parentDir.deleteEntry(blockList, canonicalPath, entry);
				}

                // then free the inode, pointerblocks and datablocks
				if (entry.bpos>0) {
					inode.openInode(entry);
					inode.free(blockList);
				}
            }
            finally {
			    vfs.flushChanges(blockList);
                vfs.getInodePool().free(inode);
                vfs.getDirPool().free(parentDir);
            }
			return true;
		}
		return false;
	}
	
	public JafsFile[] listFiles() throws JafsException, IOException {
		String parent = canonicalPath;
		if (!parent.endsWith("/")) {
		    parent += "/";
        }
		JafsDirEntry entry = getEntry(canonicalPath);
		if (entry!=null) {
			if (entry.bpos==0) {
				return new JafsFile[0];
			}
			else {
			    JafsInode inode = vfs.getInodePool().get();
                JafsDir dir = vfs.getDirPool().get();
			    try {
                    inode.openInode(entry);
                    String l[];
                    dir.setInode(inode);
                    l = dir.list();
                    JafsFile fl[] = new JafsFile[l.length];
                    for (int n=0; n<fl.length; n++) {
                        fl[n] = new JafsFile(vfs, parent + l[n]);
                    }
                    return fl;
                }
                finally {
                    vfs.getInodePool().free(inode);
                    vfs.getDirPool().free(dir);
                }

			}
		}
		return new JafsFile[0];
	}
	
	public void renameTo(JafsFile target) throws JafsException, IOException {
		if (exists()) {
			if (!target.exists()) {
				if (exists(target.getParent())) {
					JafsDirEntry entry = getEntry(canonicalPath);
					JafsInode inodeSrc = vfs.getInodePool().get();
                    JafsInode inodeDst = vfs.getInodePool().get();
                    JafsDir srcDir = vfs.getDirPool().get();
                    JafsDir dstDir = vfs.getDirPool().get();
                    Set<Long> blockList = new TreeSet<>();
					try {
                        inodeSrc.openInode(entry.parentBpos, entry.parentIpos);
                        inodeDst.openInode(getEntry(target.getParent()));
                        srcDir.setInode(inodeSrc);
                        dstDir.setInode(inodeDst);
                        srcDir.deleteEntry(blockList, canonicalPath, entry);
                        entry.name = target.getName().getBytes(Util.UTF8);
                        dstDir.createNewEntry(
                        		blockList,
                                target.canonicalPath,
                                target.getName().getBytes(Util.UTF8), entry.type, entry.bpos, entry.ipos);
                    }
                    finally {
					    vfs.flushChanges(blockList);
					    vfs.getInodePool().free(inodeSrc);
					    vfs.getInodePool().free(inodeDst);
                        vfs.getDirPool().free(srcDir);
                        vfs.getDirPool().free(dstDir);
                    }
				}
			}
		}
	}

	/* 
	 * default 
	 */
	JafsDirEntry getEntry(String path) throws JafsException, IOException {
	    String normPath = getCanonicalPath(normalizePath(path));

        if (normPath.equals("/")) {
            return vfs.getRootEntry();
        }

        String parts[] = SLASH.split(normPath);// first entry is always empty
        int n=parts.length;

        JafsDirEntry entry=null;

        String curPath = normPath;
        while (n>0) {
            entry = dc.get(curPath);
            if (entry!=null) {
                break;
            }
            curPath = getParent(curPath);
            n--;
        }

        if (n==parts.length) {
            return entry;
        }

        if (n==0) {
            entry = vfs.getRootEntry();
            curPath="";
            n=1;
        }

        if (entry.bpos==0) {
            return null;
        }

        JafsInode inode = vfs.getInodePool().get();
        JafsDir dir = vfs.getDirPool().get();
        try {
            inode.openInode(entry);
            dir.setInode(inode);
            for (; n < parts.length; n++) {
                String part = parts[n];
                if (part.length() != 0) {
                    curPath += "/" + part;
                    entry = dir.getEntry(part.getBytes(Util.UTF8));
                    if (entry == null) {
                        break;
                    } else {
                        if (n == (parts.length - 1)) {
                            // The last part of the path? Then it exists.
                            dc.add(curPath, entry);
                            break;
                        } else if (entry.isFile()) {
                            // Files should always be last part of the path.
                            entry = null;
                            break;
                        } else {
                            dc.add(curPath, entry);
                            if (entry.bpos != 0) {
                                inode.openInode(entry);
                                dir.setInode(inode);
                            } else {
                                entry = null;
                                break;
                            }
                        }
                    }
                }
            }
        }
        finally {
            vfs.getInodePool().free(inode);
            vfs.getDirPool().free(dir);
        }
		return entry;
	}
	
	/*
	 * private
	 */	
	private static String normalizePath(String path) {
		path = path.trim();
		path = MULTIPLE_SLASH.matcher(path).replaceAll("/");
		int len = path.length();
		if (len>1) {
			// Remove trailing slash except when it is the root slash
            if (path.charAt(len-1)=='/') {
                path = path.substring(0, len-1);
            }
		}
		return path;
	}
		
	private String getName(String path) {
		path = normalizePath(path);
		if (path.length()==1 && path.charAt(0)=='/') {
			return "";
		}
		String names[] = SLASH.split(path);
		return names[names.length-1];
	}
	
	private String getParent(String path) {
		boolean hasRoot = false;
		path = normalizePath(path);
		if (path.length()==1 && path.charAt(0)=='/') {
		    return null;
        }
		if (path.startsWith("/")) {
			hasRoot = true;
			path = path.substring(1);
		}
		if (!path.contains("/")) {
			if (hasRoot) {
				return "/";
			}
			else {
				return null;
			}
		}
		if (hasRoot) {
			return "/" + FILENAME_IN_PATH.matcher(path).replaceAll("");
		}
		else {

			return FILENAME_IN_PATH.matcher(path).replaceAll("");
		}
	}

	private String getAbsolutePath(String path) {
		path = normalizePath(path);
		// The working dir is always the root (/)
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		return path;
	}
		
	private static String getCanonicalPath(String path) throws JafsException {
	    // only call this method with a normalized path!
	    if (path.charAt(path.length()-1)!='/') {
	        path = path + "/";
        }
		int len = 0;
		while (len!=path.length()) {
			len = path.length();
			path = SLASH_DOT_SLASH.matcher(path).replaceAll("/");
		}
		len = 0;
		while (len!=path.length()) {
			len = path.length();
			path = FILE_CANCELS_ITSELF.matcher(path).replaceAll("");
		}
		if (path.startsWith("/../")) {
		    throw new JafsException("Parent directory (..) must not go beyond root");
        }
        if (path.equals("/")) {
		    return path;
        }
        else {
            return path.substring(0, path.length() - 1);
        }
	}
		
	private boolean exists(String path) throws JafsException, IOException {
		return getEntry(path) != null;
	}
	
	private boolean mkdir(Set<Long> blockList, String path) throws JafsException, IOException {
		String parent = getParent(path);
		JafsDirEntry entry = getEntry(parent);
		if (entry!=null) {
			if (entry.bpos==0) {
				// Parent exists but has no inode yet
                JafsInode inode = vfs.getInodePool().get();
                JafsDir dir = vfs.getDirPool().get();
                try {
                    inode.openInode(entry.parentBpos, entry.parentIpos);
                    dir.setInode(inode);
                    dir.mkinode(blockList, entry, JafsInode.INODE_DIR);
                }
                finally {
                    vfs.getInodePool().free(inode);
                    vfs.getDirPool().free(dir);
                }
			}
			JafsInode inode = vfs.getInodePool().get();
            JafsDir dir = vfs.getDirPool().get();
			try {
                inode.openInode(entry);
                dir.setInode(inode);
                dir.createNewEntry(
                		blockList,
                        getCanonicalPath(normalizePath(path)),
                        getName(path).getBytes(Util.UTF8),
                        JafsInode.INODE_DIR,
                        0,
                        0);
                return true;
			}
			catch (JafsException e) {
				return false;
			}
			finally {
			    vfs.getInodePool().free(inode);
                vfs.getDirPool().free(dir);
            }
		}
		return false;		
	}
		
	private void mkParentDirs(Set<Long> blockList, String path) throws JafsException, IOException {
		if (path==null) {
			return;
		}
		if (!exists(path)) {
			boolean result = mkdir(blockList, path);
			if (!result) {
				mkParentDirs(blockList, getParent(path));
				mkdir(blockList, path);
			}
		}
	}
}
