import sys
with open("pom.xml") as f:
    text = f.read()
    if "log4j-layout-template-yaml" in text and "log4j-layout-template-structured" in text:
        sys.exit(0)
    else:
        sys.exit(1)
