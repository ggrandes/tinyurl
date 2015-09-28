# TinyURL

TinyURL is a Simple URL Shortener build on Java. Project is Open source (Apache License, Version 2.0) 

### Current Stable Version is [1.0.3](https://maven-release.s3.amazonaws.com/release/org/javastack/tinyurl/1.0.3/tinyurl-1.0.3.war)

---

## DOC

#### Config File

    # SystemProperty / Default value
    org.javastack.tinyurl.config=[classpath]/org.javastack.tinyurl.properties

#### Config Parameters

    # Parameter / Default value
	storage.dir=[webapp]/WEB-INF/storage/
	whitelist.file=file://[storage.dir]/whitelist.conf
	check.flags=WHITELIST,CONNECTION
	check.cache.millis=60000
	connection.timeout.millis=10000
	read.timeout.millis=30000
	dump.key=[random]
	#
	# Default KVStore Persistence
	storage.class=org.javastack.tinyurl.PersistentKVStore
	#
	# Optional MySQL Persistence (default: not enabled)
	storage.class=org.javastack.tinyurl.jdbc.PersistentMySQL
	storage.url=jdbc:mysql://localhost:3306/tinyurl
	storage.username=tinyurl
	storage.password=tinyurl

* **storage.dir**: Where the local files are stored.
* **whitelist.file**: Where the whitelist file are stored.
* **check.flags**: That checks are made against URLs.
    * WHITELIST: Check URL domain against whitelist file, if not found, shortener will be denied.
    * SURBL: Check URL domain against SURBL service, if found, shortener will be denied.
    * CONNECTION: Check URL with a HTTP connection (GET). 
* **check.cache.millis**: Cache time for URL domain checks (WhiteList / SURBL).
* **connection.timeout.millis**: Connection timeout in millis.
* **read.timeout.millis**: Read timeout in millis.
* **dump.key**: Dump Key for export all storage in CSV.
* **storage.class**: Class used for persistence:
    * `org.javastack.tinyurl.PersistentKVStore`: KVStore persistence (default, portable)
    * `org.javastack.tinyurl.jdbc.PersistentMySQL`: MySQL persistence
        * **storage.url**: URL for jdbc connection
        * **storage.username**: username
        * **storage.password**: password
        * **storage.XXX**: see extra [parameters](https://tomcat.apache.org/tomcat-7.0-doc/jdbc-pool.html#Common_Attributes), all prefixed with **storage.**

###### More examples

* More examples in [sampleconf directory](https://github.com/ggrandes/tinyurl/tree/master/sampleconf/)

## Running (Tomcat)

* Set `CATALINA_OPTS="-Dorg.javastack.tinyurl.config=file://${CATALINA_BASE}/conf/org.javastack.tinyurl.properties"` in tomcat/bin/setenv.sh (linux)
* Copy war file inside webapps directory with name: ROOT.war
* For MySQL persistence: 
    * Copy [mysql-connector-java-X.X.XX.jar](http://search.maven.org/#search|gav|1|g%3A"mysql"%20AND%20a%3A"mysql-connector-java") in tomcat/lib/
    * Create MySQL user With: `GRANT ALL ON tinyurl.* TO 'tinyurl'@'%' IDENTIFIED BY 'secret';`

---

## MISC
Current hardcoded values:

* Default config file (searched in classpath): org.javastack.tinyurl.properties
* Default checks for URLs are: WHITELIST,CONNECTION
* Default checks cache (millis): 60000
* Default Connection Timeout (millis): 10000
* Default Read Timeout (millis): 30000
* Algorithm for generate Keys from URL: MD5
* The `KEY_SPACE` is: 6 characters (base64 is 64^KS(6) = 68.719.476.736 keys max)
* Default Backend for storage is: [KVStore](https://github.com/ggrandes/kvstore/) (portable)


---
Inspired in [goo.gl](https://goo.gl/), [bit.ly](https://bitly.com/) and [cort.as](http://cortas.elpais.com/), this code is Java-minimalistic version.
