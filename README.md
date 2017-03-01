# CBM-Changes-Explorer

Application for use in testing and understanding interaction of Couchbase Lite, Sync Gateway, and the Sync Gateway changes feed that drives replication.

## To build

`mvn jfx:jar`

## To run

`java -cp target/CBMChangesExplorer-1.0.0.jar com.couchbase.mobile.Main`

## Create Mac app bundle

`mvn package appbundle:bundle`

Bundle is in `target/CBMChangesExplorer-1.0.0`

## Create .dmg

Set `<generateDiskImageFile>false</generateDiskImageFile>` to true in pom.xml.  Run maven bundle command.
