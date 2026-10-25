#!/bin/sh
set -eu

target=/home/semin5/SRGHIT-ha/nginx-ha.conf
backup=/home/semin5/SRGHIT-ha/nginx-ha.conf.before-rental-20261003
cp -p "$target" "$backup"
cp /tmp/nginx-ha-rental-20261003.conf "$target"
if ! docker exec SRGH_IT-nginx nginx -t; then
    cp -p "$backup" "$target"
    exit 1
fi
docker exec SRGH_IT-nginx nginx -s reload
