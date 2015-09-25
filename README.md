# TinyURL

TinyURL is a Simple URL Shortener build on Java. Project is Open source (Apache License, Version 2.0) 

### Current Stable Version is [1.0.0](https://maven-release.s3.amazonaws.com/release/org/javastack/tinyurl/1.0.0/tinyurl-1.0.0.war)

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
	connection.timeout.millis=10000
	read.timeout.millis=30000

* **storage.dir**: Where the files are stored.
* **whitelist.file**: Where the whitelist file are stored.
* **check.flags**: That checks are made against URLs.
    * WHITELIST: Check URL domain against whitelist file, if not found, shortener will be denied.
    * SURBL: Check URL domain against SURBL service, if found, shortener will be denied.
    * CONNECTION: Check URL host with a HTTP connection (HEAD). 
* **connection.timeout.millis**: Connection timeout in millis.
* **read.timeout.millis**: Read timeout in millis.

###### More examples

* More examples in [sampleconf directory](https://github.com/ggrandes/tinyurl/tree/master/sampleconf/)

---
Inspired in [goo.gl](https://goo.gl/), [bit.ly](https://bitly.com/) and [cort.as](http://cortas.elpais.com/), this code is Java-minimalistic version.
