# TinyURL

TinyURL is a Simple URL Shortener build on Java. Project is Open source (Apache License, Version 2.0) 

### Current Stable Version is [1.0.2](https://maven-release.s3.amazonaws.com/release/org/javastack/tinyurl/1.0.2/tinyurl-1.0.2.war)

---

## DOC

#### Config File

    # SystemProperty / Default value
    org.javastack.tinyurl.config=[classpath]/org.javastack.tinyurl.properties

#### Config Parameters

    # Parameter / Default value
	storage.dir=[webapp]/WEB-INF/storage/
	whitelist.file=file://[storage.dir]/whitelist.conf
	check.flags=WHITELIST,SURBL,CONNECTION
	check.cache.millis=60000
	connection.timeout.millis=10000
	read.timeout.millis=30000
	dump.key=[random]

* **storage.dir**: Where the files are stored.
* **whitelist.file**: Where the whitelist file are stored.
* **check.flags**: That checks are made against URLs.
    * WHITELIST: Check URL domain against whitelist file, if not found, shortener will be denied.
    * SURBL: Check URL domain against SURBL service, if found, shortener will be denied.
    * CONNECTION: Check URL with a HTTP connection (GET). 
* **check.cache.millis**: Cache time for URL domain checks (WhiteList / SURBL).
* **connection.timeout.millis**: Connection timeout in millis.
* **read.timeout.millis**: Read timeout in millis.
* **dump.key**: Dump Key for export all storage in CSV.

###### More examples

* More examples in [sampleconf directory](https://github.com/ggrandes/tinyurl/tree/master/sampleconf/)

## Running (Tomcat)

* Set `CATALINA_OPTS="-Dorg.javastack.tinyurl.config=file://${CATALINA_BASE}/conf/org.javastack.tinyurl.properties"` in tomcat/bin/setenv.sh (linux)
* Copy war file inside webapps directory with name: ROOT.war

---

## TODOs

* MySQL backed for storage

## MISC
Current harcoded values:

* Default config file (searched in classpath): org.javastack.tinyurl.properties
* Default checks for URLs are: WHITELIST,SURBL,CONNECTION
* Default checks cache (millis): 60000
* Default Connection Timeout (millis): 10000
* Default Read Timeout (millis): 30000
* Algorithm for generate Keys from URL: MD5
* The `KEY_SPACE` is: 6 characters (base64 is 6bits^KS(6) = 46.656 keys max)
* Backend for storage is: [KVStore](https://github.com/ggrandes/kvstore/) (portable)


---
Inspired in [goo.gl](https://goo.gl/), [bit.ly](https://bitly.com/) and [cort.as](http://cortas.elpais.com/), this code is Java-minimalistic version.
