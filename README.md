# cbs-message-visualiser
Tool for parsing a MongoDB containing messages from CBS and for indexing its content to a Neo4j database.

## Build from source
<code>mvn clean install shade:shade -Dmaven.test.skip=true</code>

## Run
<code>java -jar target/original-cbs-message-visualiser-1.0-SNAPSHOT.jar mongodb://\<host\>:\<port\>/\<dbname\>/\<collection-name\></code>
