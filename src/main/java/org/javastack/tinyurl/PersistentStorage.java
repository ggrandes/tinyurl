package org.javastack.tinyurl;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.log4j.Logger;
import org.javastack.kvstore.KVStoreFactory;
import org.javastack.kvstore.holders.DataHolder;
import org.javastack.kvstore.io.FileStreamStore;
import org.javastack.kvstore.io.StringSerializer;
import org.javastack.kvstore.structures.btree.BplusTree.InvalidDataException;
import org.javastack.kvstore.structures.btree.BplusTreeFile;

class PersistentStorage implements Persistence {
	private static final Logger log = Logger.getLogger(PersistentStorage.class);
	private static final int BUF_LEN = 0x10000;
	private final KVStoreFactory<TokenHolder, MetaHolder> fac = new KVStoreFactory<TokenHolder, MetaHolder>(
			TokenHolder.class, MetaHolder.class);
	private final BplusTreeFile<TokenHolder, MetaHolder> map;
	private final FileStreamStore stream;
	private final ByteBuffer wbuf, rbuf;

	public PersistentStorage(final String storeDirName) throws InstantiationException,
			IllegalAccessException, IOException {
		final File storeDir = new File(storeDirName);
		if (!storeDir.exists()) {
			if (!storeDir.mkdirs())
				throw new IOException("Invalid storeDir: " + storeDirName);
		}
		final File storeTree = new File(storeDir, "tree");
		final File storeStream = new File(storeDir, "stream");
		wbuf = ByteBuffer.allocate(BUF_LEN);
		rbuf = ByteBuffer.allocate(BUF_LEN);
		map = fac.createTreeFile(fac.createTreeOptionsDefault()
				.set(KVStoreFactory.FILENAME, storeTree.getCanonicalPath())
				.set(KVStoreFactory.DISABLE_POPULATE_CACHE, true));
		stream = new FileStreamStore(storeStream, BUF_LEN);
		stream.setAlignBlocks(true);
		stream.setFlushOnWrite(true);
	}

	@Override
	public void open() throws IOException {
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
		synchronized (rbuf) {
			rbuf.clear();
			stream.read(meta.offset, rbuf);
			meta.url = StringSerializer.fromBufferToString(rbuf);
		}
		log.info("Found meta [" + meta + "]");
		return meta;
	}

	@Override
	public void remove(final String k) {
		map.remove(TokenHolder.valueOf(k));
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
		private final int timestamp;
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
}