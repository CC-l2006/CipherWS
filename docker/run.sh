#!/bin/bash

NACOS_AUTH_TOKEN=N2FjM2Y4YjEtZGM3OS00ZjI1LThhYjYtMTIzNDU2Nzg5MGFi
NACOS_AUTH_IDENTITY_KEY=nacos_identity_key_9f8e7d6c
NACOS_AUTH_IDENTITY_VALUE=nacos_identity_value_3a2b1c0d9e8f7a6b

docker run --name nacos-standalone-derby \
    -e MODE=standalone \
    -e NACOS_AUTH_TOKEN=${NACOS_AUTH_TOKEN} \
    -e NACOS_AUTH_IDENTITY_KEY=${NACOS_AUTH_IDENTITY_KEY} \
    -e NACOS_AUTH_IDENTITY_VALUE=${NACOS_AUTH_IDENTITY_VALUE} \
    -p 8849:8080 \
    -p 8848:8848 \
    -p 9848:9848 \
    -d nacos/nacos-server:v3.3.0-RC
