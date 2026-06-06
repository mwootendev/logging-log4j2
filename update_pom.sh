#!/bin/bash
sed -i '/<dependencies>/a \    <dependency>\n      <groupId>com.fasterxml.jackson.dataformat</groupId>\n      <artifactId>jackson-dataformat-yaml</artifactId>\n      <scope>test</scope>\n    </dependency>' log4j-layout-template-yaml-test/pom.xml
