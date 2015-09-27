package org.javastack.tinyurl;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Iterator;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.javastack.kvstore.KVStoreFactory;
import org.javastack.kvstore.holders.DataHolder;
import org.javastack.kvstore.io.FileStreamStore;
import org.javastack.kvstore.io.StringSerializer;
import org.javastack.kvstore.structures.btree.BplusTree.InvalidDataException;
import org.javastack.kvstore.structures.btree.BplusTree.TreeEntry;
import org.javastack.kvstore.structures.btree.BplusTreeFile;
import org.javastack.stringproperties.StringProperties;

public class PersistentKVStore implements Persistence {
	private static final Logger log = Logger.getLogger(PersistentKVStore.class);
	private static final int BUF_LEN = 0x10000;
	private final KVStoreFactory<TokenHolder, MetaHolder> fac = new KVStoreFactory<TokenHolder, MetaHolder>(
			TokenHolder.class, MetaHolder.class);
	private final ByteBuffer wbuf, rbuf;
	private String storeDirName = System.getProperty("java.io.tmpdir", "/tmp/");
	private BplusTreeFile<TokenHolder, MetaHolder> map = null;
	private FileStreamStore stream = null;

	public PersistentKVStore() {
		wbuf = ByteBuffer.allocate(BUF_LEN);
		rbuf = ByteBuffer.allocate(BUF_LEN);
	}

	@Override
	public void configure(final StringProperties properties) {
		storeDirName = properties.getProperty("dir");
		log.info("Storage config={dir=" + this.storeDirName + "}");
	}

	@Override
	public void open() throws IOException {
		final File storeDir = new File(storeDirName);
		if (!storeDir.exists()) {
			if (!storeDir.mkdirs())
				throw new IOException("Invalid storeDir: " + storeDirName);
		}
		final File storeTree = new File(storeDir, "tree");
		final File storeStream = new File(storeDir, "stream");
		try {
			map = fac.createTreeFile(fac.createTreeOptionsDefault()
					.set(KVStoreFactory.FILENAME, storeTree.getCanonicalPath())
					.set(KVStoreFactory.DISABLE_POPULATE_CACHE, true));
		} catch (IllegalAccessException e) {
			throw new IOException(e);
		} catch (InstantiationException e) {
			throw new IOException(e);
		}
		stream = new FileStreamStore(storeStream, BUF_LEN);
		stream.setAlignBlocks(true);
		stream.setFlushOnWrite(true);
		try {
			if (map.open())
				log.info("open tree ok");
		} catch (InvalidDataException e) {
			log.error("open tree error, recovery needed");
			try {
				if (map.recovery(false) && map.open()) {
					log.info("recovery ok, tree opened");
				} else {
					throw new IOException(e);
				}
			} catch (InvalidDataException ee) {
				throw new IOException(ee);
			}
		}
		stream.open();
	}

	@Override
	public void close() {
		stream.close();
		map.close();
	}

	@Override
	public void put(final String k, final String v) {
		long offset = -1;
		synchronized (wbuf) {
			wbuf.clear();
			StringSerializer.fromStringToBuffer(wbuf, v);
			wbuf.flip();
			offset = stream.write(wbuf);
		}
		map.put(TokenHolder.valueOf(k), MetaHolder.valueOf(offset));
	}

	@Override
	public TinyData get(final String k) {
		final MetaHolder meta = map.get(TokenHolder.valueOf(k));
		if (meta == null)
			return null;
		readExternal(meta);
		log.info("Found meta id=" + k + " [" + meta + "]");
		return meta;
	}

	private void readExternal(final MetaHolder meta) {
		synchronized (rbuf) {
			rbuf.clear();
			stream.read(meta.offset, rbuf);
			meta.url = StringSerializer.fromBufferToString(rbuf);
		}
	}

	@Override
	public void remove(final String k) {
		map.remove(TokenHolder.valueOf(k));
	}

	@Override
	public void dump(final OutputStream out) throws IOException {
		final Charset iso = Charset.forName("ISO-8859-1");
		final byte[] CRLF = "\r\n".getBytes(iso);
		final Iterator<TreeEntry<TokenHolder, MetaHolder>> i = map.iterator();
		out.write("token,url,created-unix-epoch-utc".getBytes(iso));
		out.write(CRLF);
		while (i.hasNext()) {
			final TreeEntry<TokenHolder, MetaHolder> e = i.next();
			final TokenHolder token = e.getKey();
			final MetaHolder meta = e.getValue();
			readExternal(meta);
			out.write(token.token.getBytes(iso));
			out.write(',');
			out.write(meta.getURL().getBytes(iso));
			out.write(',');
			out.write(Long.toString(meta.timestamp).getBytes(iso));
			out.write(CRLF);
		}
		out.flush();
	}

	public static class TokenHolder extends DataHolder<TokenHolder> {
		private final String token;

		public TokenHolder() {
			this("");
		}

		public TokenHolder(final String token) {
			this.token = token;
		}

		public static TokenHolder valueOf(final String token) {
			return new TokenHolder(token);
		}

		@Override
		public int compareTo(final TokenHolder other) {
			return token.compareTo(other.token);
		}

		@Override
		public boolean equals(final Object other) {
			if (!(other instanceof TokenHolder))
				return false;
			return token.equals(((TokenHolder) other).token);
		}

		@Override
		public int hashCode() {
			return token.hashCode();
		}

		@Override
		public String toString() {
			return token;
		}

		@Override
		public int byteLength() {
			return 4 + Constants.KEY_SPACE + 1;
		}

		@Override
		public void serialize(final ByteBuffer bb) {
			StringSerializer.fromStringToBuffer(bb, token);
		}

		@Override
		public TokenHolder deserialize(final ByteBuffer bb) {
			return TokenHolder.valueOf(StringSerializer.fromBufferToString(bb));
		}
	}

	public static class MetaHolder extends DataHolder<MetaHolder> implements TinyData {
		private final long offset;
		private final int timestamp; // creation
		// Stored in Secondary Stream (pointed by offset)
		private String url = null;

		public MetaHolder() {
			this(0, 0);
		}

		public MetaHolder(final long offset, final int timestamp) {
			this.offset = offset;
			this.timestamp = timestamp;
		}

		public static MetaHolder valueOf(final long offset) {
			return new MetaHolder(offset, (int) (System.currentTimeMillis() / 1000));
		}

		@Override
		public int compareTo(final MetaHolder o) {
			if (offset < o.offset)
				return -1;
			if (offset > o.offset)
				return 1;
			if (timestamp < o.timestamp)
				return -1;
			if (timestamp > o.timestamp)
				return 1;
			return 0;
		}

		@Override
		public boolean equals(final Object other) {
			if (!(other instanceof MetaHolder))
				return false;
			return (compareTo((MetaHolder) other) == 0);
		}

		@Override
		public int hashCode() {
			return (int) (offset ^ (offset >>> 32));
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder(32);
			sb.append("offset=").append(offset).append(" timestamp=").append(timestamp);
			return sb.toString();
		}

		@Override
		public int byteLength() {
			return 8 + 4;
		}

		@Override
		public void serialize(final ByteBuffer bb) {
			bb.putLong(offset);
			bb.putInt(timestamp);
		}

		@Override
		public MetaHolder deserialize(final ByteBuffer bb) {
			return new MetaHolder(bb.getLong(), bb.getInt());
		}

		@Override
		public String getURL() {
			return url;
		}
	}

	/**
	 * Simple command line Tool
	 */
	public static void main(final String[] args) throws Throwable {
		if (args.length != 1) {
			System.out.println(PersistentKVStore.class.getName() + " <directory-of-storage>");
			System.exit(1);
		}
		final File dir = new File(args[0]);
		if (!dir.isDirectory()) {
			throw new FileNotFoundException("Directory not found: " + dir.getAbsolutePath());
		}
		Logger.getRootLogger().setLevel(Level.ERROR);
		final PersistentKVStore storage = new PersistentKVStore();
		final BufferedOutputStream out = new BufferedOutputStream(System.out, 4096);
		final StringProperties conf = new StringProperties();
		conf.setProperty("dir", dir.getAbsolutePath());
		try {
			storage.configure(conf);
			storage.open();
			storage.dump(out);
		} finally {
			storage.close();
			out.flush();
		}
	}
}