#!/bin/bash
# PHANTOM Mining Run Script

# Set Java heap size for large datasets
JAVA_OPTS="-Xmx4g -Xms2g"

# Run with all arguments passed through
java $JAVA_OPTS -jar target/phantom-mining-1.0-SNAPSHOT-jar-with-dependencies.jar "$@"